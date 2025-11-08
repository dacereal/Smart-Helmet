#!/usr/bin/env python3
"""
Flask server for drowsiness detection on Raspberry Pi
Receives camera frames from Android app and returns detection results
"""

from flask import Flask, request, jsonify
from flask_cors import CORS
import cv2
import numpy as np
import base64
import logging
from drowsiness_detector import DrowsinessDetector
import os

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = Flask(__name__)
CORS(app)  # Enable CORS for Android app

# Initialize detector
MODEL_PATH = os.path.join(os.path.dirname(__file__), 'models', 'my_model.onnx')
if not os.path.exists(MODEL_PATH):
    # Fallback to parent directory
    MODEL_PATH = os.path.join(os.path.dirname(__file__), '..', 'app', 'src', 'main', 'assets', 'my_model.onnx')

detector = None

def init_detector():
    """Initialize the drowsiness detector"""
    global detector
    try:
        if os.path.exists(MODEL_PATH):
            detector = DrowsinessDetector(MODEL_PATH)
            logger.info("Drowsiness detector initialized successfully")
        else:
            logger.error(f"Model file not found at: {MODEL_PATH}")
            logger.error("Please ensure my_model.onnx is in the models/ directory")
    except Exception as e:
        logger.error(f"Failed to initialize detector: {e}")
        detector = None

@app.route('/health', methods=['GET'])
def health_check():
    """Health check endpoint"""
    return jsonify({
        'status': 'ok',
        'detector_loaded': detector is not None,
        'model_path': MODEL_PATH
    })

@app.route('/detect', methods=['POST'])
def detect_drowsiness():
    """
    Detect drowsiness from image frame
    
    Request body (JSON):
    {
        "image": "base64_encoded_image_string"
    }
    
    Response:
    {
        "is_drowsy": bool,
        "confidence": float,
        "detections": [
            {
                "bbox": [x1, y1, x2, y2],
                "confidence": float,
                "class_id": int,
                "label": str
            }
        ]
    }
    """
    if detector is None:
        return jsonify({
            'error': 'Detector not initialized'
        }), 500
    
    try:
        # Get image data from request
        data = request.get_json()
        
        if not data or 'image' not in data:
            return jsonify({'error': 'No image data provided'}), 400
        
        # Decode base64 image
        image_data = data['image']
        
        # Handle data URL format (data:image/jpeg;base64,...)
        if ',' in image_data:
            image_data = image_data.split(',')[1]
        
        # Decode base64
        image_bytes = base64.b64decode(image_data)
        
        # Convert to numpy array
        nparr = np.frombuffer(image_bytes, np.uint8)
        
        # Decode image
        image = cv2.imdecode(nparr, cv2.IMREAD_COLOR)
        
        if image is None:
            return jsonify({'error': 'Failed to decode image'}), 400
        
        # Run detection
        result = detector.get_detection_result(image)
        
        return jsonify(result)
        
    except Exception as e:
        logger.error(f"Detection error: {e}", exc_info=True)
        return jsonify({'error': str(e)}), 500

@app.route('/detect_simple', methods=['POST'])
def detect_drowsiness_simple():
    """
    Simplified endpoint that returns only is_drowsy boolean
    
    Request body (JSON):
    {
        "image": "base64_encoded_image_string"
    }
    
    Response:
    {
        "is_drowsy": bool,
        "confidence": float
    }
    """
    if detector is None:
        return jsonify({
            'error': 'Detector not initialized'
        }), 500
    
    try:
        data = request.get_json()
        
        if not data or 'image' not in data:
            return jsonify({'error': 'No image data provided'}), 400
        
        # Decode image
        image_data = data['image']
        if ',' in image_data:
            image_data = image_data.split(',')[1]
        
        image_bytes = base64.b64decode(image_data)
        nparr = np.frombuffer(image_bytes, np.uint8)
        image = cv2.imdecode(nparr, cv2.IMREAD_COLOR)
        
        if image is None:
            return jsonify({'error': 'Failed to decode image'}), 400
        
        # Quick detection
        is_drowsy = detector.is_drowsy(image)
        
        # Get confidence if drowsy
        confidence = 0.0
        if is_drowsy:
            result = detector.get_detection_result(image)
            confidence = result['confidence']
        
        return jsonify({
            'is_drowsy': is_drowsy,
            'confidence': confidence
        })
        
    except Exception as e:
        logger.error(f"Detection error: {e}", exc_info=True)
        return jsonify({'error': str(e)}), 500

if __name__ == '__main__':
    # Initialize detector before starting server
    init_detector()
    
    if detector is None:
        logger.warning("Starting server without detector - endpoints will return errors")
    
    # Run server
    # Use 0.0.0.0 to allow connections from other devices on the network
    app.run(host='0.0.0.0', port=5000, debug=False, threaded=True)

