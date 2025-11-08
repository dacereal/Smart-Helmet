package com.botsquad.smarthelmet;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

/**
 * Manages Android device's built-in camera for drowsiness detection
 * Replaces ESP32 camera streaming
 */
public class AndroidCameraManager {
    private static final String TAG = "AndroidCameraManager";
    private static final int PREVIEW_WIDTH = 640;
    private static final int PREVIEW_HEIGHT = 480;
    
    private Context context;
    private CameraManager cameraManager;
    private String cameraId;
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private ImageReader imageReader;
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;
    private SurfaceView previewSurfaceView;
    private SurfaceHolder previewSurfaceHolder;
    
    private CameraFrameCallback frameCallback;
    private boolean isCapturing = false;
    
    /**
     * Callback interface for receiving camera frames
     */
    public interface CameraFrameCallback {
        void onFrameReceived(Bitmap frame);
        void onError(Exception error);
    }
    
    public AndroidCameraManager(Context context) {
        this.context = context;
        this.cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
    }
    
    /**
     * Initialize camera with preview surface
     */
    public void initialize(SurfaceView previewSurfaceView) {
        this.previewSurfaceView = previewSurfaceView;
        this.previewSurfaceHolder = previewSurfaceView.getHolder();
        
        try {
            // Find front-facing camera (for driver monitoring)
            cameraId = getFrontCameraId();
            if (cameraId == null) {
                Log.e(TAG, "No front-facing camera found, trying any available camera");
                String[] cameraIds = cameraManager.getCameraIdList();
                if (cameraIds.length > 0) {
                    cameraId = cameraIds[0];
                } else {
                    throw new RuntimeException("No cameras available");
                }
            }
            
            Log.d(TAG, "Using camera: " + cameraId);
            
            // Create background thread for camera operations
            startBackgroundThread();
            
        } catch (Exception e) {
            Log.e(TAG, "Error initializing camera: " + e.getMessage(), e);
            if (frameCallback != null) {
                frameCallback.onError(e);
            }
        }
    }
    
    /**
     * Start camera preview and capture
     */
    public void startCapture(CameraFrameCallback callback) {
        if (isCapturing) {
            Log.w(TAG, "Camera already capturing");
            return;
        }
        
        this.frameCallback = callback;
        
        try {
            // Create ImageReader for capturing frames (use YUV format for ImageReader)
            imageReader = ImageReader.newInstance(
                PREVIEW_WIDTH, 
                PREVIEW_HEIGHT, 
                ImageFormat.YUV_420_888, 
                2 // Buffer count
            );
            
            imageReader.setOnImageAvailableListener(reader -> {
                Image image = reader.acquireLatestImage();
                if (image != null) {
                    processImage(image);
                    image.close();
                }
            }, backgroundHandler);
            
            // Open camera
            cameraManager.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(CameraDevice camera) {
                    cameraDevice = camera;
                    createCaptureSession();
                }
                
                @Override
                public void onDisconnected(CameraDevice camera) {
                    closeCamera();
                }
                
                @Override
                public void onError(CameraDevice camera, int error) {
                    Log.e(TAG, "Camera error: " + error);
                    closeCamera();
                    if (frameCallback != null) {
                        frameCallback.onError(new RuntimeException("Camera error: " + error));
                    }
                }
            }, backgroundHandler);
            
            isCapturing = true;
            
        } catch (CameraAccessException e) {
            Log.e(TAG, "Error starting camera capture: " + e.getMessage(), e);
            if (frameCallback != null) {
                frameCallback.onError(e);
            }
        }
    }
    
    /**
     * Create camera capture session
     */
    private void createCaptureSession() {
        try {
            if (cameraDevice == null) {
                Log.e(TAG, "Camera device is null, cannot create capture session");
                return;
            }
            
            if (previewSurfaceHolder == null || previewSurfaceHolder.getSurface() == null) {
                Log.e(TAG, "Preview surface is not ready yet");
                return;
            }
            
            Surface previewSurface = previewSurfaceHolder.getSurface();
            if (!previewSurface.isValid()) {
                Log.e(TAG, "Preview surface is not valid");
                return;
            }
            
            Surface imageReaderSurface = imageReader.getSurface();
            Log.d(TAG, "Creating capture session with preview surface and ImageReader");
            
            List<Surface> surfaces = Arrays.asList(previewSurface, imageReaderSurface);
            
            cameraDevice.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    if (cameraDevice == null) {
                        return;
                    }
                    
                    captureSession = session;
                    
                    try {
                        // Create capture request for preview
                        CaptureRequest.Builder previewRequestBuilder = 
                            cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                        previewRequestBuilder.addTarget(previewSurface);
                        previewRequestBuilder.addTarget(imageReaderSurface);
                        
                        // Set auto-focus
                        previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, 
                            CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                        
                        // Set auto-exposure
                        previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, 
                            CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
                        
                        // Start repeating requests for continuous capture
                        CaptureRequest previewRequest = previewRequestBuilder.build();
                        captureSession.setRepeatingRequest(previewRequest, null, backgroundHandler);
                        
                        Log.d(TAG, "Camera capture session started");
                        
                    } catch (CameraAccessException e) {
                        Log.e(TAG, "Error creating capture request: " + e.getMessage(), e);
                        if (frameCallback != null) {
                            frameCallback.onError(e);
                        }
                    }
                }
                
                @Override
                public void onConfigureFailed(CameraCaptureSession session) {
                    Log.e(TAG, "Failed to configure capture session");
                    if (frameCallback != null) {
                        frameCallback.onError(new RuntimeException("Failed to configure capture session"));
                    }
                }
            }, backgroundHandler);
            
        } catch (CameraAccessException e) {
            Log.e(TAG, "Error creating capture session: " + e.getMessage(), e);
            if (frameCallback != null) {
                frameCallback.onError(e);
            }
        }
    }
    
    /**
     * Process captured image frame
     */
    private void processImage(Image image) {
        try {
            if (frameCallback == null) {
                return;
            }
            
            // Convert Image to Bitmap
            Bitmap bitmap = imageToBitmap(image);
            if (bitmap != null) {
                frameCallback.onFrameReceived(bitmap);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error processing image: " + e.getMessage(), e);
            if (frameCallback != null) {
                frameCallback.onError(e);
            }
        }
    }
    
    /**
     * Convert Image (YUV_420_888) to Bitmap
     */
    private Bitmap imageToBitmap(Image image) {
        try {
            // Get Y plane (luminance)
            Image.Plane[] planes = image.getPlanes();
            ByteBuffer yBuffer = planes[0].getBuffer();
            ByteBuffer uBuffer = planes[1].getBuffer();
            ByteBuffer vBuffer = planes[2].getBuffer();
            
            int ySize = yBuffer.remaining();
            int uSize = uBuffer.remaining();
            int vSize = vBuffer.remaining();
            
            byte[] nv21 = new byte[ySize + uSize + vSize];
            
            yBuffer.get(nv21, 0, ySize);
            vBuffer.get(nv21, ySize, vSize);
            uBuffer.get(nv21, ySize + vSize, uSize);
            
            // Convert YUV to RGB Bitmap
            android.graphics.YuvImage yuvImage = new android.graphics.YuvImage(
                nv21, 
                android.graphics.ImageFormat.NV21, 
                image.getWidth(), 
                image.getHeight(), 
                null
            );
            
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            yuvImage.compressToJpeg(
                new android.graphics.Rect(0, 0, image.getWidth(), image.getHeight()), 
                100, 
                out
            );
            
            byte[] imageBytes = out.toByteArray();
            return android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
            
        } catch (Exception e) {
            Log.e(TAG, "Error converting image to bitmap: " + e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Stop camera capture
     */
    public void stopCapture() {
        isCapturing = false;
        closeCamera();
        stopBackgroundThread();
    }
    
    /**
     * Close camera resources
     */
    private void closeCamera() {
        try {
            if (captureSession != null) {
                captureSession.close();
                captureSession = null;
            }
            if (cameraDevice != null) {
                cameraDevice.close();
                cameraDevice = null;
            }
            if (imageReader != null) {
                imageReader.close();
                imageReader = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error closing camera: " + e.getMessage(), e);
        }
    }
    
    /**
     * Get front-facing camera ID
     */
    private String getFrontCameraId() throws CameraAccessException {
        String[] cameraIds = cameraManager.getCameraIdList();
        for (String id : cameraIds) {
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(id);
            Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
            if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                return id;
            }
        }
        return null;
    }
    
    /**
     * Start background thread for camera operations
     */
    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("CameraBackground");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }
    
    /**
     * Stop background thread
     */
    private void stopBackgroundThread() {
        if (backgroundThread != null) {
            backgroundThread.quitSafely();
            try {
                backgroundThread.join();
                backgroundThread = null;
                backgroundHandler = null;
            } catch (InterruptedException e) {
                Log.e(TAG, "Error stopping background thread: " + e.getMessage(), e);
            }
        }
    }
    
    /**
     * Check if camera is available
     */
    public boolean isCameraAvailable() {
        try {
            String[] cameraIds = cameraManager.getCameraIdList();
            return cameraIds.length > 0;
        } catch (Exception e) {
            return false;
        }
    }
}

