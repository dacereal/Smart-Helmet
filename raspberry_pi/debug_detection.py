#!/usr/bin/env python3
"""
Debug script to see exactly what the ONNX model outputs vs what ultralytics does
"""

import cv2
import numpy as np
from drowsiness_detector import DrowsinessDetector
import os
import logging

logging.basicConfig(level=logging.DEBUG)  # Enable debug logging

# Try to import ultralytics
try:
    from ultralytics import YOLO
    ULTRALYTICS_AVAILABLE = True
except ImportError:
    ULTRALYTICS_AVAILABLE = False
    print("Warning: ultralytics not available")

# Model path
ONNX_MODEL_PATH = "my_model.onnx"
if not os.path.exists(ONNX_MODEL_PATH):
    ONNX_MODEL_PATH = os.path.join("..", "app", "src", "main", "assets", "my_model.onnx")

def test_with_real_image():
    """Test with a real image from camera"""
    print("=" * 60)
    print("DEBUGGING DETECTION DIFFERENCES")
    print("=" * 60)
    
    # Capture from camera
    cap = cv2.VideoCapture(0)
    if not cap.isOpened():
        print("ERROR: Cannot open camera")
        return
    
    print("\nCapturing frame from camera...")
    ret, frame = cap.read()
    cap.release()
    
    if not ret:
        print("ERROR: Failed to capture frame")
        return
    
    print(f"Frame shape: {frame.shape}")
    
    # Test with ONNX detector
    print("\n" + "=" * 60)
    print("TESTING ONNX DETECTOR (Your Pi Server Code)")
    print("=" * 60)
    detector = DrowsinessDetector(ONNX_MODEL_PATH, confidence_threshold=0.5)
    onnx_result = detector.get_detection_result(frame)
    
    print(f"\nONNX Result:")
    print(f"  is_drowsy: {onnx_result['is_drowsy']}")
    print(f"  confidence: {onnx_result['confidence']:.4f}")
    print(f"  num_detections: {len(onnx_result['detections'])}")
    for det in onnx_result['detections']:
        print(f"    - {det['label']} (ID: {det['class_id']}), conf: {det['confidence']:.4f}, bbox: {det['bbox']}")
    
    # Test with ultralytics
    if ULTRALYTICS_AVAILABLE:
        print("\n" + "=" * 60)
        print("TESTING ULTRALYTICS YOLO (Your PyCharm Test)")
        print("=" * 60)
        model = YOLO(ONNX_MODEL_PATH)
        results = model.predict(source=frame, show=False, conf=0.5, verbose=True)
        
        print(f"\nUltralytics Result:")
        for r in results:
            print(f"  Number of boxes: {len(r.boxes)}")
            is_drowsy = False
            max_conf = 0.0
            best_det = None
            
            for box in r.boxes:
                cls = int(box.cls[0])
                conf = float(box.conf[0])
                label = r.names[cls]
                xyxy = box.xyxy[0].cpu().numpy()
                
                print(f"    - {label} (ID: {cls}), conf: {conf:.4f}, bbox: {xyxy}")
                
                if cls == 1 and conf > max_conf:  # Class 1 is Drowsy
                    is_drowsy = True
                    max_conf = conf
                    best_det = {'label': label, 'class_id': cls, 'confidence': conf, 'bbox': xyxy}
            
            print(f"\n  is_drowsy: {is_drowsy}")
            print(f"  confidence: {max_conf:.4f}")
            
            # Compare
            print("\n" + "=" * 60)
            print("COMPARISON")
            print("=" * 60)
            print(f"ONNX:      is_drowsy={onnx_result['is_drowsy']}, confidence={onnx_result['confidence']:.4f}")
            print(f"Ultralytics: is_drowsy={is_drowsy}, confidence={max_conf:.4f}")
            
            if onnx_result['is_drowsy'] == is_drowsy and abs(onnx_result['confidence'] - max_conf) < 0.1:
                print("\n✅ RESULTS MATCH!")
            else:
                print("\n❌ RESULTS DIFFER!")
                print("\nThis means the postprocessing needs to be fixed.")
                print("The issue is likely in how we interpret the YOLO output format.")
    else:
        print("\nUltralytics not available - install with: pip install ultralytics")

if __name__ == "__main__":
    test_with_real_image()

