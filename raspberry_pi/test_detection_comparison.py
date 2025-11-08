#!/usr/bin/env python3
"""
Test script to compare detection results between ultralytics YOLO and ONNX detector
Run this to verify they produce the same results
"""

import cv2
import numpy as np
from drowsiness_detector import DrowsinessDetector
import os

# Try to import ultralytics (optional)
try:
    from ultralytics import YOLO
    ULTRALYTICS_AVAILABLE = True
except ImportError:
    ULTRALYTICS_AVAILABLE = False
    print("Warning: ultralytics not available. Install with: pip install ultralytics")

# Model paths
ONNX_MODEL_PATH = "my_model.onnx"
if not os.path.exists(ONNX_MODEL_PATH):
    ONNX_MODEL_PATH = os.path.join("..", "app", "src", "main", "assets", "my_model.onnx")

def test_with_onnx_detector(image_path_or_camera=0):
    """Test with ONNX detector"""
    print("\n=== Testing ONNX Detector ===")
    
    detector = DrowsinessDetector(ONNX_MODEL_PATH, confidence_threshold=0.5)
    
    if isinstance(image_path_or_camera, str):
        image = cv2.imread(image_path_or_camera)
    else:
        cap = cv2.VideoCapture(image_path_or_camera)
        ret, image = cap.read()
        cap.release()
        if not ret:
            print("Failed to capture from camera")
            return
    
    result = detector.get_detection_result(image)
    
    print(f"is_drowsy: {result['is_drowsy']}")
    print(f"confidence: {result['confidence']:.4f}")
    print(f"detections: {len(result['detections'])}")
    for det in result['detections']:
        print(f"  - Class: {det['label']} (ID: {det['class_id']}), Confidence: {det['confidence']:.4f}")
    
    return result

def test_with_ultralytics(image_path_or_camera=0):
    """Test with ultralytics YOLO"""
    if not ULTRALYTICS_AVAILABLE:
        print("\n=== Ultralytics YOLO not available ===")
        return None
    
    print("\n=== Testing Ultralytics YOLO ===")
    
    model = YOLO(ONNX_MODEL_PATH)
    
    results = model.predict(source=image_path_or_camera, show=False, conf=0.5, verbose=False)
    
    for r in results:
        print(f"Number of detections: {len(r.boxes)}")
        is_drowsy = False
        max_conf = 0.0
        
        for box in r.boxes:
            cls = int(box.cls[0])
            conf = float(box.conf[0])
            label = r.names[cls]
            
            print(f"  - Class: {label} (ID: {cls}), Confidence: {conf:.4f}")
            
            if cls == 1 and conf > max_conf:  # Class 1 is Drowsy
                is_drowsy = True
                max_conf = conf
        
        result = {
            'is_drowsy': is_drowsy,
            'confidence': max_conf,
            'detections': []
        }
        
        return result
    
    return None

if __name__ == "__main__":
    print("Drowsiness Detection Comparison Test")
    print("=" * 50)
    
    # Test with camera (0) or image path
    source = 0  # Change to image path if you want to test with an image
    
    # Test ONNX detector
    onnx_result = test_with_onnx_detector(source)
    
    # Test ultralytics YOLO
    ultralytics_result = test_with_ultralytics(source)
    
    # Compare results
    if onnx_result and ultralytics_result:
        print("\n=== Comparison ===")
        print(f"ONNX - is_drowsy: {onnx_result['is_drowsy']}, confidence: {onnx_result['confidence']:.4f}")
        print(f"Ultralytics - is_drowsy: {ultralytics_result['is_drowsy']}, confidence: {ultralytics_result['confidence']:.4f}")
        
        if onnx_result['is_drowsy'] == ultralytics_result['is_drowsy']:
            print("✅ Results match!")
        else:
            print("❌ Results differ!")
            print("This indicates a postprocessing issue that needs to be fixed.")
