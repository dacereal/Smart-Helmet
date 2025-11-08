package com.botsquad.smarthelmet;

import android.graphics.Bitmap;
import android.util.Log;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PiDrowsinessDetector {
    private static final String TAG = "PiDrowsinessDetector";
    private static final String DEFAULT_PI_URL = "http://192.168.43.151:5000";
    private String piServerUrl;
    private ExecutorService executorService;
    
    // Detection result class (compatible with DrowsinessDetector.Detection)
    public static class Detection {
        public final android.graphics.RectF box;
        public final float score;
        public final int cls;
        public final String label;
        
        public Detection(android.graphics.RectF box, float score, int cls, String label) {
            this.box = box;
            this.score = score;
            this.cls = cls;
            this.label = label;
        }
    }
    
    // Result callback interface
    public interface DetectionCallback {
        void onDetectionComplete(List<Detection> detections, boolean isDrowsy, float confidence);
        void onError(Exception error);
    }
    
    public PiDrowsinessDetector(String piServerUrl) {
        this.piServerUrl = piServerUrl != null && !piServerUrl.isEmpty() 
            ? piServerUrl 
            : DEFAULT_PI_URL;
        this.executorService = Executors.newSingleThreadExecutor();
        Log.d(TAG, "PiDrowsinessDetector initialized with URL: " + this.piServerUrl);
    }
    
    public PiDrowsinessDetector() {
        this(DEFAULT_PI_URL);
    }
    
    /**
     * Convert bitmap to base64 string
     */
    private String bitmapToBase64(Bitmap bitmap) {
        try {
            // Compress to JPEG with 80% quality for faster transmission
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream);
            byte[] imageBytes = outputStream.toByteArray();
            
            // Convert to base64
            return android.util.Base64.encodeToString(imageBytes, android.util.Base64.NO_WRAP);
        } catch (Exception e) {
            Log.e(TAG, "Error converting bitmap to base64: " + e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Detect drowsiness by sending image to Raspberry Pi
     */
    public void detectAsync(Bitmap bitmap, DetectionCallback callback) {
        if (bitmap == null || callback == null) {
            Log.e(TAG, "Bitmap or callback is null");
            if (callback != null) {
                callback.onError(new IllegalArgumentException("Bitmap or callback is null"));
            }
            return;
        }
        
        executorService.execute(() -> {
            try {
                // Convert bitmap to base64
                String base64Image = bitmapToBase64(bitmap);
                if (base64Image == null) {
                    callback.onError(new Exception("Failed to encode image"));
                    return;
                }
                
                // Create JSON request
                JSONObject requestJson = new JSONObject();
                requestJson.put("image", base64Image);
                
                // Send HTTP POST request
                URL url = new URL(piServerUrl + "/detect");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("Accept", "application/json");
                connection.setConnectTimeout(5000); // 5 second timeout
                connection.setReadTimeout(10000); // 10 second timeout
                connection.setDoOutput(true);
                
                // Send request body
                String requestBody = requestJson.toString();
                byte[] requestBodyBytes = requestBody.getBytes("UTF-8");
                connection.setRequestProperty("Content-Length", String.valueOf(requestBodyBytes.length));
                
                try (OutputStream os = connection.getOutputStream()) {
                    os.write(requestBodyBytes);
                    os.flush();
                }
                
                // Get response
                int responseCode = connection.getResponseCode();
                Log.d(TAG, "Response code: " + responseCode);
                
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    // Read response
                    Scanner scanner = new Scanner(connection.getInputStream(), "UTF-8");
                    String responseBody = scanner.useDelimiter("\\A").hasNext() ? scanner.next() : "";
                    scanner.close();
                    
                    // Parse JSON response
                    JSONObject responseJson = new JSONObject(responseBody);
                    boolean isDrowsy = responseJson.optBoolean("is_drowsy", false);
                    double confidenceValue = responseJson.optDouble("confidence", 0.0);
                    float confidence = (float) confidenceValue;
                    
                    // Parse detections array if available
                    List<Detection> detections = new ArrayList<>();
                    if (responseJson.has("detections")) {
                        org.json.JSONArray detectionsArray = responseJson.getJSONArray("detections");
                        for (int i = 0; i < detectionsArray.length(); i++) {
                            JSONObject detJson = detectionsArray.getJSONObject(i);
                            float detConfidence = (float) detJson.optDouble("confidence", 0.0);
                            int classId = detJson.optInt("class_id", 0);
                            String label = detJson.optString("label", classId == 1 ? "Drowsy" : "Alert");
                            
                            // Parse bounding box if available
                            android.graphics.RectF box = null;
                            if (detJson.has("bbox")) {
                                org.json.JSONArray bboxArray = detJson.getJSONArray("bbox");
                                if (bboxArray.length() >= 4) {
                                    float x1 = (float) bboxArray.getDouble(0);
                                    float y1 = (float) bboxArray.getDouble(1);
                                    float x2 = (float) bboxArray.getDouble(2);
                                    float y2 = (float) bboxArray.getDouble(3);
                                    box = new android.graphics.RectF(x1, y1, x2, y2);
                                }
                            }
                            
                            // Create a default box if not provided
                            if (box == null) {
                                box = new android.graphics.RectF(0, 0, bitmap.getWidth(), bitmap.getHeight());
                            }
                            
                            detections.add(new Detection(box, detConfidence, classId, label));
                        }
                    } else if (isDrowsy) {
                        // If is_drowsy is true but no detections array, create a default detection
                        android.graphics.RectF defaultBox = new android.graphics.RectF(0, 0, bitmap.getWidth(), bitmap.getHeight());
                        detections.add(new Detection(defaultBox, confidence, 1, "Drowsy"));
                    }
                    
                    // Call callback on main thread
                    callback.onDetectionComplete(detections, isDrowsy, confidence);
                    
                } else {
                    // Read error response
                    Scanner scanner = new Scanner(connection.getErrorStream(), "UTF-8");
                    String errorBody = scanner.useDelimiter("\\A").hasNext() ? scanner.next() : "";
                    scanner.close();
                    Log.e(TAG, "Server error response: " + errorBody);
                    callback.onError(new Exception("Server error: " + responseCode + " - " + errorBody));
                }
                
                connection.disconnect();
                
            } catch (Exception e) {
                Log.e(TAG, "Error during detection: " + e.getMessage(), e);
                callback.onError(e);
            }
        });
    }
    
    /**
     * Simple detection - just returns is_drowsy boolean (faster endpoint)
     */
    public void isDrowsyAsync(Bitmap bitmap, DetectionCallback callback) {
        if (bitmap == null || callback == null) {
            if (callback != null) {
                callback.onError(new IllegalArgumentException("Bitmap or callback is null"));
            }
            return;
        }
        
        executorService.execute(() -> {
            try {
                // Convert bitmap to base64
                String base64Image = bitmapToBase64(bitmap);
                if (base64Image == null) {
                    callback.onError(new Exception("Failed to encode image"));
                    return;
                }
                
                // Create JSON request
                JSONObject requestJson = new JSONObject();
                requestJson.put("image", base64Image);
                
                // Send HTTP POST request to simpler endpoint
                URL url = new URL(piServerUrl + "/detect_simple");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("Accept", "application/json");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(10000);
                connection.setDoOutput(true);
                
                // Send request body
                String requestBody = requestJson.toString();
                byte[] requestBodyBytes = requestBody.getBytes("UTF-8");
                connection.setRequestProperty("Content-Length", String.valueOf(requestBodyBytes.length));
                
                try (OutputStream os = connection.getOutputStream()) {
                    os.write(requestBodyBytes);
                    os.flush();
                }
                
                // Get response
                int responseCode = connection.getResponseCode();
                
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    // Read response
                    Scanner scanner = new Scanner(connection.getInputStream(), "UTF-8");
                    String responseBody = scanner.useDelimiter("\\A").hasNext() ? scanner.next() : "";
                    scanner.close();
                    
                    // Parse JSON response
                    JSONObject responseJson = new JSONObject(responseBody);
                    boolean isDrowsy = responseJson.optBoolean("is_drowsy", false);
                    double confidenceValue = responseJson.optDouble("confidence", 0.0);
                    float confidence = (float) confidenceValue;
                    
                    // Create minimal detection list
                    List<Detection> detections = new ArrayList<>();
                    if (isDrowsy) {
                        android.graphics.RectF defaultBox = new android.graphics.RectF(0, 0, bitmap.getWidth(), bitmap.getHeight());
                        detections.add(new Detection(defaultBox, confidence, 1, "Drowsy"));
                    }
                    
                    // Call callback on main thread
                    callback.onDetectionComplete(detections, isDrowsy, confidence);
                    
                } else {
                    Scanner scanner = new Scanner(connection.getErrorStream(), "UTF-8");
                    String errorBody = scanner.useDelimiter("\\A").hasNext() ? scanner.next() : "";
                    scanner.close();
                    callback.onError(new Exception("Server error: " + responseCode + " - " + errorBody));
                }
                
                connection.disconnect();
                
            } catch (Exception e) {
                Log.e(TAG, "Error during simple detection: " + e.getMessage(), e);
                callback.onError(e);
            }
        });
    }
    
    /**
     * Query Pi for current detection result (Pi Camera mode)
     * The Pi captures frames from its own camera and runs detection
     */
    public void queryDetectionAsync(DetectionCallback callback) {
        if (callback == null) {
            return;
        }
        
        executorService.execute(() -> {
            try {
                // GET request to Pi (no image needed - Pi uses its own camera)
                URL url = new URL(piServerUrl + "/detect_simple");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Accept", "application/json");
                connection.setConnectTimeout(5000);  // Increased to 5 seconds
                connection.setReadTimeout(10000);  // Increased to 10 seconds for slow Pi
                
                int responseCode = connection.getResponseCode();
                Log.d(TAG, "Query response code: " + responseCode);
                
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    // Read response
                    Scanner scanner = new Scanner(connection.getInputStream(), "UTF-8");
                    String responseBody = scanner.useDelimiter("\\A").hasNext() ? scanner.next() : "";
                    scanner.close();
                    
                    // Parse JSON response
                    JSONObject responseJson = new JSONObject(responseBody);
                    boolean isDrowsy = responseJson.optBoolean("is_drowsy", false);
                    double confidenceValue = responseJson.optDouble("confidence", 0.0);
                    float confidence = (float) confidenceValue;
                    
                    // Create minimal detection list
                    List<Detection> detections = new ArrayList<>();
                    if (isDrowsy) {
                        // Create a default detection (no bounding box since Pi processes it)
                        android.graphics.RectF defaultBox = new android.graphics.RectF(0, 0, 640, 480);
                        detections.add(new Detection(defaultBox, confidence, 1, "Drowsy"));
                    }
                    
                    // Call callback
                    callback.onDetectionComplete(detections, isDrowsy, confidence);
                    
                } else {
                    Scanner scanner = new Scanner(connection.getErrorStream(), "UTF-8");
                    String errorBody = scanner.useDelimiter("\\A").hasNext() ? scanner.next() : "";
                    scanner.close();
                    callback.onError(new Exception("Server error: " + responseCode + " - " + errorBody));
                }
                
                connection.disconnect();
                
            } catch (Exception e) {
                Log.e(TAG, "Error querying Pi detection: " + e.getMessage(), e);
                callback.onError(e);
            }
        });
    }
    
    /**
     * Check if Pi server is reachable
     */
    public void checkHealthAsync(HealthCallback callback) {
        executorService.execute(() -> {
            try {
                URL url = new URL(piServerUrl + "/health");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(3000);
                connection.setReadTimeout(3000);
                
                int responseCode = connection.getResponseCode();
                boolean isHealthy = (responseCode == HttpURLConnection.HTTP_OK);
                
                String responseBody = "";
                if (isHealthy) {
                    Scanner scanner = new Scanner(connection.getInputStream(), "UTF-8");
                    responseBody = scanner.useDelimiter("\\A").hasNext() ? scanner.next() : "";
                    scanner.close();
                }
                
                connection.disconnect();
                
                callback.onHealthChecked(isHealthy, responseBody);
                
            } catch (Exception e) {
                Log.e(TAG, "Health check failed: " + e.getMessage(), e);
                callback.onHealthChecked(false, e.getMessage());
            }
        });
    }
    
    public interface HealthCallback {
        void onHealthChecked(boolean isHealthy, String response);
    }
    
    public void close() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }
}

