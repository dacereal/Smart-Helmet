package com.botsquad.smarthelmet;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class YoloPlaceholderDetector {
    private static final String TAG = "YoloPlaceholderDetector";
    private final int inputSize;
    private final float scoreThreshold;
    private final float nmsThreshold;

    public static class Detection {
        public final RectF box;
        public final float score;
        public final int cls;
        
        public Detection(RectF box, float score, int cls) {
            this.box = box;
            this.score = score;
            this.cls = cls;
        }
    }

    public YoloPlaceholderDetector(Context context, String modelAssetName, int inputSize, float scoreThreshold, float nmsThreshold) throws Exception {
        this.inputSize = inputSize;
        this.scoreThreshold = scoreThreshold;
        this.nmsThreshold = nmsThreshold;
        
        Log.d(TAG, "Placeholder YOLO detector initialized - AI model will be added later");
    }

    public List<Detection> detect(Bitmap src) {
        // Placeholder implementation - returns empty list for now
        // This allows the app to run without ONNX dependency
        Log.d(TAG, "Placeholder detection called - no actual inference performed");
        
        // Simulate some processing time
        try {
            Thread.sleep(50); // Small delay to simulate processing
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Return empty list - no detections for now
        return new ArrayList<>();
    }

    public void close() {
        Log.d(TAG, "Placeholder detector closed");
    }
}

