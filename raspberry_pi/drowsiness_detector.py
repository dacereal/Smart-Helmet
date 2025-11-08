#!/usr/bin/env python3
"""
Drowsiness Detection Service for Raspberry Pi 5
Uses ONNX Runtime for optimized inference
"""

import cv2
import numpy as np
import onnxruntime as ort
import os
import json
from typing import List, Tuple, Optional
import logging

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


class DrowsinessDetector:
    """ONNX-based drowsiness detector optimized for Raspberry Pi 5"""
    
    def __init__(self, model_path: str, input_size: int = 640, confidence_threshold: float = 0.5):
        """
        Initialize the drowsiness detector
        
        Args:
            model_path: Path to ONNX model file
            input_size: Model input size (default 640 for YOLO)
            confidence_threshold: Minimum confidence for detection (default 0.5)
        """
        self.input_size = input_size
        self.confidence_threshold = confidence_threshold
        
        # Check if model exists
        if not os.path.exists(model_path):
            raise FileNotFoundError(f"Model file not found: {model_path}")
        
        # Initialize ONNX Runtime session with optimizations for Raspberry Pi
        sess_options = ort.SessionOptions()
        sess_options.graph_optimization_level = ort.GraphOptimizationLevel.ORT_ENABLE_ALL
        sess_options.intra_op_num_threads = 4  # Use all 4 cores of Pi 5
        sess_options.inter_op_num_threads = 4
        
        # Use CPU execution provider (optimized for ARM64)
        providers = ['CPUExecutionProvider']
        
        try:
            self.session = ort.InferenceSession(
                model_path,
                sess_options=sess_options,
                providers=providers
            )
            logger.info(f"Model loaded successfully from {model_path}")
        except Exception as e:
            logger.error(f"Failed to load ONNX model: {e}")
            raise
        
        # Get input/output names and shapes
        self.input_name = self.session.get_inputs()[0].name
        self.output_names = [output.name for output in self.session.get_outputs()]
        
        input_shape = self.session.get_inputs()[0].shape
        output_shapes = [output.shape for output in self.session.get_outputs()]
        logger.info(f"Model input shape: {input_shape}")
        logger.info(f"Model output names: {self.output_names}")
        logger.info(f"Model output shapes: {output_shapes}")
        
        # Class labels
        self.class_labels = {0: "Alert", 1: "Drowsy"}
        
    def preprocess_image(self, image: np.ndarray) -> np.ndarray:
        """
        Preprocess image for model input
        
        Args:
            image: Input image (BGR format from OpenCV)
            
        Returns:
            Preprocessed image array ready for inference
        """
        # Resize to model input size
        resized = cv2.resize(image, (self.input_size, self.input_size), interpolation=cv2.INTER_LINEAR)
        
        # Convert BGR to RGB
        rgb = cv2.cvtColor(resized, cv2.COLOR_BGR2RGB)
        
        # Normalize to [0, 1] and convert to float32
        normalized = rgb.astype(np.float32) / 255.0
        
        # Transpose from HWC to CHW format (height, width, channels -> channels, height, width)
        transposed = np.transpose(normalized, (2, 0, 1))
        
        # Add batch dimension
        batched = np.expand_dims(transposed, axis=0)
        
        return batched
    
    def postprocess_yolo_output(self, outputs: List[np.ndarray], 
                                original_shape: Tuple[int, int]) -> List[dict]:
        """
        Postprocess YOLO model output to match ultralytics YOLO behavior
        
        Handles two possible output formats:
        1. Raw YOLO output: [batch, num_detections, 4 + num_classes]
           - First 4: bbox (x_center, y_center, width, height) normalized [0,1]
           - Rest: class probabilities
        2. Post-processed with NMS: [batch, num_detections, 6]
           - [x1, y1, x2, y2, confidence, class_id] (already in pixel coordinates)
        
        Args:
            outputs: Model output arrays
            original_shape: (height, width) of original image
            
        Returns:
            List of detection dictionaries
        """
        detections = []
        output = outputs[0]
        
        # Remove batch dimension if present
        if len(output.shape) == 3:
            output = output[0]  # Shape: [num_detections, features]
        
        orig_h, orig_w = original_shape
        num_features = output.shape[1]
        
        # Determine output format based on number of features
        if num_features == 6:
            # Format 2: Post-processed with NMS [x1, y1, x2, y2, confidence, class_id]
            logger.debug("Detected post-processed YOLO output format (with NMS)")
            for i in range(output.shape[0]):
                x1 = float(output[i, 0])
                y1 = float(output[i, 1])
                x2 = float(output[i, 2])
                y2 = float(output[i, 3])
                confidence = float(output[i, 4])
                class_id = int(output[i, 5])
                
                # Skip if confidence is below threshold or invalid bbox
                if confidence < self.confidence_threshold or x2 <= x1 or y2 <= y1:
                    continue
                
                # Clamp to image bounds
                x1 = max(0, min(x1, orig_w))
                y1 = max(0, min(y1, orig_h))
                x2 = max(0, min(x2, orig_w))
                y2 = max(0, min(y2, orig_h))
                
                detections.append({
                    'bbox': [x1, y1, x2, y2],
                    'confidence': confidence,
                    'class_id': class_id,
                    'label': self.class_labels.get(class_id, f"Class_{class_id}")
                })
        else:
            # Format 1: Raw YOLO output [x_center, y_center, width, height, class_probs...]
            logger.debug(f"Detected raw YOLO output format ({num_features} features)")
            num_classes = num_features - 4
            
            for i in range(output.shape[0]):
                # Extract bbox (normalized [0,1])
                x_center_norm = float(output[i, 0])
                y_center_norm = float(output[i, 1])
                width_norm = float(output[i, 2])
                height_norm = float(output[i, 3])
                
                # Extract class probabilities
                class_probs = output[i, 4:4+num_classes]
                
                # Find class with highest probability
                class_id = int(np.argmax(class_probs))
                confidence = float(class_probs[class_id])
                
                # Skip if confidence is below threshold
                if confidence < self.confidence_threshold:
                    continue
                
                # Scale bbox coordinates to original image size
                x_center = x_center_norm * orig_w
                y_center = y_center_norm * orig_h
                width = width_norm * orig_w
                height = height_norm * orig_h
                
                # Convert from center format to corner format
                x1 = x_center - width / 2
                y1 = y_center - height / 2
                x2 = x_center + width / 2
                y2 = y_center + height / 2
                
                # Clamp to image bounds
                x1 = max(0, min(x1, orig_w))
                y1 = max(0, min(y1, orig_h))
                x2 = max(0, min(x2, orig_w))
                y2 = max(0, min(y2, orig_h))
                
                detections.append({
                    'bbox': [x1, y1, x2, y2],
                    'confidence': confidence,
                    'class_id': class_id,
                    'label': self.class_labels.get(class_id, f"Class_{class_id}")
                })
        
        # Apply simple NMS (Non-Maximum Suppression) to remove overlapping detections
        # Keep only the detection with highest confidence for each class
        if detections:
            # Group by class
            by_class = {}
            for det in detections:
                class_id = det['class_id']
                if class_id not in by_class:
                    by_class[class_id] = []
                by_class[class_id].append(det)
            
            # Keep best detection per class
            final_detections = []
            for class_id, class_dets in by_class.items():
                best = max(class_dets, key=lambda x: x['confidence'])
                final_detections.append(best)
            
            return final_detections
        
        return detections
    
    def detect(self, image: np.ndarray) -> List[dict]:
        """
        Run inference on an image
        
        Args:
            image: Input image (BGR format from OpenCV)
            
        Returns:
            List of detection dictionaries with bbox, confidence, class_id, label
        """
        original_shape = (image.shape[0], image.shape[1])
        
        # Preprocess
        preprocessed = self.preprocess_image(image)
        
        # Run inference
        try:
            outputs = self.session.run(self.output_names, {self.input_name: preprocessed})
        except Exception as e:
            logger.error(f"Inference error: {e}")
            return []
        
        # Postprocess
        detections = self.postprocess_yolo_output(outputs, original_shape)
        
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
        Get detailed detection result
        
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

