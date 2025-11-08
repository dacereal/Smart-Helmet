#!/usr/bin/env python3
"""
Alternative detector that uses ultralytics YOLO directly for postprocessing
This ensures 100% compatibility with PyCharm test
"""

import cv2
import numpy as np
import os
import logging
from typing import List, Tuple

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

try:
    from ultralytics import YOLO
    ULTRALYTICS_AVAILABLE = True
except ImportError:
    ULTRALYTICS_AVAILABLE = False
    logger.error("ultralytics not available. Install with: pip install ultralytics")


class DrowsinessDetectorUltralytics:
    """Drowsiness detector using ultralytics YOLO for exact PyCharm compatibility"""
    
    def __init__(self, model_path: str, confidence_threshold: float = 0.5):
        """
        Initialize the drowsiness detector using ultralytics YOLO
        
        Args:
            model_path: Path to ONNX model file
            confidence_threshold: Minimum confidence for detection (default 0.5)
        """
        if not ULTRALYTICS_AVAILABLE:
            raise ImportError("ultralytics is required. Install with: pip install ultralytics")
        
        self.confidence_threshold = confidence_threshold
        
        # Check if model exists
        if not os.path.exists(model_path):
            raise FileNotFoundError(f"Model file not found: {model_path}")
        
        # Load model with ultralytics (handles ONNX automatically)
        self.model = YOLO(model_path)
        logger.info(f"Model loaded successfully from {model_path} using ultralytics")
        
        # Class labels
        self.class_labels = {0: "Alert", 1: "Drowsy"}
    
    def detect(self, image: np.ndarray) -> List[dict]:
        """
        Run inference on an image using ultralytics YOLO
        
        Args:
            image: Input image (BGR format from OpenCV)
            
        Returns:
            List of detection dictionaries with bbox, confidence, class_id, label
        """
        # Run prediction with ultralytics (same as PyCharm test)
        results = self.model.predict(
            source=image,
            show=False,
            conf=self.confidence_threshold,
            verbose=False
        )
        
        detections = []
        
        for r in results:
            for box in r.boxes:
                cls = int(box.cls[0])
                conf = float(box.conf[0])
                xyxy = box.xyxy[0].cpu().numpy()  # [x1, y1, x2, y2]
                
                detections.append({
                    'bbox': [float(xyxy[0]), float(xyxy[1]), float(xyxy[2]), float(xyxy[3])],
                    'confidence': conf,
                    'class_id': cls,
                    'label': r.names[cls]
                })
        
        return detections
    
    def is_drowsy(self, image: np.ndarray) -> bool:
        """
        Quick check if image contains drowsy person
        
        Args:
            image: Input image (BGR format from OpenCV)
            
        Returns:
            True if drowsy detected, False otherwise
        """
        detections = self.detect(image)
        
        for detection in detections:
            if detection['class_id'] == 1 and detection['confidence'] >= self.confidence_threshold:
                return True
        
        return False
    
    def get_detection_result(self, image: np.ndarray) -> dict:
        """
        Get detailed detection result (same format as original detector)
        
        Args:
            image: Input image (BGR format from OpenCV)
            
        Returns:
            Dictionary with is_drowsy, confidence, and detection details
        """
        detections = self.detect(image)
        
        result = {
            'is_drowsy': False,
            'confidence': 0.0,
            'detections': []
        }
        
        # Find best drowsy detection (class_id == 1)
        drowsy_detections = [d for d in detections if d['class_id'] == 1]
        
        if drowsy_detections:
            best = max(drowsy_detections, key=lambda x: x['confidence'])
            result['is_drowsy'] = True
            result['confidence'] = best['confidence']
            result['detections'] = [best]
        elif detections:
            # Return alert detection if available
            best = max(detections, key=lambda x: x['confidence'])
            result['confidence'] = best['confidence']
            result['detections'] = [best]
        
        return result

