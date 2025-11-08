package com.botsquad.smarthelmet;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.util.Log;

import org.tensorflow.lite.Interpreter;
// import org.tensorflow.lite.gpu.GpuDelegate; // Removed to fix crash
import org.tensorflow.lite.support.common.FileUtil;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.ResizeOp;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

public class DrowsinessDetector {
    private static final String TAG = "DrowsinessDetector";
    private static final String MODEL_FILE = "my_model_float32.tflite";
    
    // Model input/output parameters - adjust based on your model
    private static final int INPUT_SIZE = 640; // Model was trained on 640x640
    private static final int NUM_CLASSES = 2; // 0: Alert, 1: Drowsy
    private static final float CONFIDENCE_THRESHOLD = 0.5f; // 50% confidence threshold (matches Python code)
    private static final int YOLO_OUTPUT_SIZE = 8400; // YOLO output size
    
    private Interpreter tflite;
    // private GpuDelegate gpuDelegate; // Removed to fix crash
    private ImageProcessor imageProcessor;
    private TensorImage inputImageBuffer;
    
    // Input and output buffers
    private ByteBuffer inputBuffer;
    private float[][][] yoloOutputArray; // YOLO output format [1, 6, 8400]
    
    public static class Detection {
        public final RectF box;
        public final float score;
        public final int cls;
        public final String label;
        
        public Detection(RectF box, float score, int cls, String label) {
            this.box = box;
            this.score = score;
            this.cls = cls;
            this.label = label;
        }
    }
    
    public DrowsinessDetector(Context context) throws IOException {
        // Initialize TensorFlow Lite interpreter
        Interpreter.Options options = new Interpreter.Options();
        
        // Use CPU only for now (GPU delegate causing crashes)
        Log.d(TAG, "Using CPU for TensorFlow Lite inference");
        
    // Optimize for performance and reduce CPU usage
    options.setNumThreads(1); // Use single thread to reduce CPU overhead
    options.setUseXNNPACK(true); // Enable XNNPACK acceleration
    options.setUseNNAPI(true); // Enable NNAPI for hardware acceleration if available
    
    // Load the model with error handling
    try {
        ByteBuffer modelBuffer = FileUtil.loadMappedFile(context, MODEL_FILE);
        tflite = new Interpreter(modelBuffer, options);
        Log.d(TAG, "Model loaded successfully with XNNPACK acceleration");
    } catch (Exception e) {
        Log.e(TAG, "Failed to load model: " + e.getMessage(), e);
        throw new IOException("Failed to load TensorFlow Lite model", e);
    }
        
        // Initialize image processor with optimized settings for performance
        imageProcessor = new ImageProcessor.Builder()
                .add(new ResizeOp(INPUT_SIZE, INPUT_SIZE, ResizeOp.ResizeMethod.NEAREST_NEIGHBOR)) // Fastest resizing method
                .build();
        
        // Initialize input image buffer
        inputImageBuffer = new TensorImage();
        
        // Initialize input buffer (640x640x3 as required by model)
        inputBuffer = ByteBuffer.allocateDirect(4 * INPUT_SIZE * INPUT_SIZE * 3); // 4 bytes per float, 3 channels
        inputBuffer.order(ByteOrder.nativeOrder());
        
        // Initialize YOLO output array
        yoloOutputArray = new float[1][6][YOLO_OUTPUT_SIZE];
        
        Log.d(TAG, "Drowsiness detector initialized successfully");
    }
    
    public List<Detection> detect(Bitmap bitmap) {
        List<Detection> detections = new ArrayList<>();
        
        try {
            // Preprocess the image
            inputImageBuffer.load(bitmap);
            inputImageBuffer = imageProcessor.process(inputImageBuffer);
            
            // Convert to ByteBuffer
            ByteBuffer byteBuffer = inputImageBuffer.getBuffer();
            inputBuffer.rewind();
            inputBuffer.put(byteBuffer);
            
            // Run inference
            tflite.run(inputBuffer, yoloOutputArray);
            
            // Process YOLO results
            float[][] yoloOutput = yoloOutputArray[0]; // [6, 8400]
            
            // YOLO format: [x, y, w, h, confidence, class]
            // For drowsiness detection, we expect class 0 (alert) and class 1 (drowsy)
            float maxConfidence = 0f;
            int bestClass = 0;
            float bestScore = 0f;
            RectF bestBox = null;
            
            for (int i = 0; i < YOLO_OUTPUT_SIZE; i++) {
                float confidence = yoloOutput[4][i]; // confidence score
                int classId = (int)yoloOutput[5][i]; // class ID (0 or 1)
                
                if (confidence > CONFIDENCE_THRESHOLD && confidence > maxConfidence) {
                    maxConfidence = confidence;
                    bestClass = classId;
                    bestScore = confidence;
                    
                    // Extract bounding box coordinates
                    float centerX = yoloOutput[0][i] * bitmap.getWidth();
                    float centerY = yoloOutput[1][i] * bitmap.getHeight();
                    float width = yoloOutput[2][i] * bitmap.getWidth();
                    float height = yoloOutput[3][i] * bitmap.getHeight();
                    
                    // Convert from center format to corner format
                    float left = centerX - width / 2;
                    float top = centerY - height / 2;
                    float right = centerX + width / 2;
                    float bottom = centerY + height / 2;
                    
                    bestBox = new RectF(left, top, right, bottom);
                }
            }
            
            Log.d(TAG, String.format("YOLO Detection - Class: %d, Confidence: %.3f", bestClass, bestScore));
            
            if (maxConfidence > CONFIDENCE_THRESHOLD && bestBox != null) {
                // Create detection with actual bounding box
                String label = bestClass == 1 ? "Drowsy" : "Alert";
                Detection detection = new Detection(bestBox, bestScore, bestClass, label);
                detections.add(detection);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error during inference: " + e.getMessage(), e);
        }
        
        return detections;
    }
    
    public boolean isDrowsy(Bitmap bitmap) {
        List<Detection> detections = detect(bitmap);
        if (!detections.isEmpty()) {
            Detection detection = detections.get(0);
            return detection.cls == 1 && detection.score > CONFIDENCE_THRESHOLD;
        }
        return false;
    }
    
    // Ultra-fast drowsiness-only detection (optimized for performance)
    public boolean isDrowsyFast(Bitmap bitmap) {
        try {
            // Preprocess the image
            inputImageBuffer.load(bitmap);
            inputImageBuffer = imageProcessor.process(inputImageBuffer);
            
            // Convert to ByteBuffer
            ByteBuffer byteBuffer = inputImageBuffer.getBuffer();
            inputBuffer.rewind();
            inputBuffer.put(byteBuffer);
            
            // Run inference
            tflite.run(inputBuffer, yoloOutputArray);
            
            // Ultra-fast check: only check first 20 detections for maximum speed
            float[][] yoloOutput = yoloOutputArray[0];
            float drowsyThreshold = CONFIDENCE_THRESHOLD; // Use same threshold as Python code (0.5)
            
            float maxConfidence = 0f;
            int bestClass = -1;
            
            for (int i = 0; i < Math.min(20, YOLO_OUTPUT_SIZE); i++) { // Only check first 20 for maximum speed
                float confidence = yoloOutput[4][i];
                int classId = (int)yoloOutput[5][i];
                
                // Track the highest confidence detection
                if (confidence > maxConfidence) {
                    maxConfidence = confidence;
                    bestClass = classId;
                }
                
                if (confidence > drowsyThreshold && classId == 1) { // Class 1 = Drowsy
                    Log.d(TAG, String.format("Ultra-Fast Detection - Class: %d, Confidence: %.3f", classId, confidence));
                    return true;
                }
            }
            
            // Log the best detection even if it doesn't meet threshold
            if (maxConfidence > 0.1f) { // Only log if there's some confidence
                Log.d(TAG, String.format("Best Detection - Class: %d, Confidence: %.3f (Threshold: %.3f)", 
                    bestClass, maxConfidence, drowsyThreshold));
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error during ultra-fast inference: " + e.getMessage(), e);
        }
        
        return false;
    }
    
    public void close() {
        if (tflite != null) {
            tflite.close();
            tflite = null;
        }
        // GPU delegate removed to fix crash
        Log.d(TAG, "Drowsiness detector closed");
    }
}
