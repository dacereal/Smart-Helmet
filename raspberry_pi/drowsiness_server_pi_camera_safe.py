#!/usr/bin/env python3
"""
Drowsiness Detection Service for Raspberry Pi 5 - SAFE MODE
Reduced frame rate and processing to prevent crashes
"""

from flask import Flask, jsonify, Response
from flask_cors import CORS
import cv2
import numpy as np
import logging
from drowsiness_detector import DrowsinessDetector
import os
import threading
import time
import gc  # Garbage collection to manage memory

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = Flask(__name__)
CORS(app)

MODEL_PATH = os.path.join(os.path.dirname(__file__), 'models', 'my_model.onnx')
if not os.path.exists(MODEL_PATH):
    MODEL_PATH = os.path.join(os.path.dirname(__file__), '..', 'app', 'src', 'main', 'assets', 'my_model.onnx')

detector = None
camera = None
current_result = None
current_frame = None  # Latest frame for streaming
frame_lock = threading.Lock()  # Lock for frame access
result_lock = threading.Lock()
capture_thread = None
is_capturing = False

def init_detector():
    """Initialize the drowsiness detector"""
    global detector
    try:
        if os.path.exists(MODEL_PATH):
            detector = DrowsinessDetector(MODEL_PATH)
            logger.info("Drowsiness detector initialized successfully")
        else:
            logger.error(f"Model file not found at: {MODEL_PATH}")
    except Exception as e:
        logger.error(f"Failed to initialize detector: {e}")
        detector = None

def init_camera():
    """Initialize Raspberry Pi Camera"""
    global camera
    try:
        try:
            from picamera2 import Picamera2
            camera = Picamera2()
            camera_config = camera.create_still_configuration(main={"size": (640, 480)})
            camera.configure(camera_config)
            camera.start()
            test_frame = camera.capture_array()
            if test_frame is not None and test_frame.size > 0:
                logger.info("Pi Camera initialized successfully (Picamera2)")
                return True
            else:
                camera.stop()
                camera = None
                return False
        except Exception as e:
            logger.error(f"Picamera2 initialization failed: {e}")
            if camera is not None:
                try:
                    camera.stop()
                except:
                    pass
                camera = None
            return False
    except Exception as e:
        logger.error(f"Failed to initialize camera: {e}")
        return False

def capture_and_detect():
    """Continuously capture frames and run detection - SAFE MODE"""
    global current_result, current_frame, is_capturing
    
    is_capturing = True
    logger.info("Starting camera capture thread (BALANCED MODE - 3 FPS capture, 5 FPS stream)")
    frame_count = 0
    
    while is_capturing:
        try:
            frame = None
            
            if hasattr(camera, 'capture_array'):
                try:
                    frame = camera.capture_array()
                    frame = cv2.cvtColor(frame, cv2.COLOR_RGB2BGR)
                except Exception as e:
                    logger.warning(f"Failed to capture frame: {e}")
                    time.sleep(1.0)  # Longer sleep on error
                    continue
            else:
                ret, frame = camera.read()
                if not ret or frame is None:
                    logger.warning("Failed to capture frame")
                    time.sleep(1.0)
                    continue
            
            if frame is None:
                time.sleep(1.0)
                continue
            
            # Store latest frame for streaming (thread-safe, but minimal storage)
            with frame_lock:
                current_frame = frame.copy()
            
            # ULTRA-LOW POWER: Only run detection every 20th frame (minimal processing)
            frame_count += 1
            if frame_count % 20 == 0 and detector is not None:
                try:
                    result = detector.get_detection_result(frame)
                    with result_lock:
                        current_result = result
                except Exception as e:
                    logger.error(f"Detection error: {e}")
            else:
                # Set default result if skipping detection
                with result_lock:
                    if current_result is None:
                        current_result = {
                            'is_drowsy': False,
                            'confidence': 0.0,
                            'detections': []
                        }
            
            # Force garbage collection periodically
            if frame_count % 10 == 0:
                gc.collect()
            
            # ULTRA-LOW POWER MODE: Very reduced frame rate (0.5 FPS for streaming)
            time.sleep(2.0)  # 2 seconds = 0.5 FPS - minimum for acceptable streaming
            
        except Exception as e:
            logger.error(f"Error in capture loop: {e}", exc_info=True)
            time.sleep(1.0)  # Longer sleep on error
    
    logger.info("Camera capture thread stopped")

@app.route('/stream', methods=['GET'])
def stream():
    """MJPEG video stream endpoint (LOW POWER MODE)"""
    def generate():
        """Generator function to stream frames at reduced rate"""
        while True:
            try:
                # Get latest frame (thread-safe)
                with frame_lock:
                    if current_frame is not None:
                        frame = current_frame.copy()
                    else:
                        # Create a black frame if no frame available
                        frame = np.zeros((480, 640, 3), dtype=np.uint8)
                
                # Encode frame as JPEG (lower quality for less CPU usage)
                encode_param = [int(cv2.IMWRITE_JPEG_QUALITY), 60]  # Lower quality = less CPU
                result, jpeg_frame = cv2.imencode('.jpg', frame, encode_param)
                
                if result:
                    # MJPEG stream format
                    yield (b'--frame\r\n'
                           b'Content-Type: image/jpeg\r\n\r\n' + 
                           jpeg_frame.tobytes() + b'\r\n')
                else:
                    logger.warning("Failed to encode frame as JPEG")
                    time.sleep(0.5)
                
                # REDUCED FRAME RATE for streaming (1 FPS to save power)
                time.sleep(1.0)
                
            except Exception as e:
                logger.error(f"Error in stream generator: {e}")
                time.sleep(1.0)
    
    return Response(generate(),
                    mimetype='multipart/x-mixed-replace; boundary=frame')

@app.route('/health', methods=['GET'])
def health_check():
    """Health check endpoint"""
    return jsonify({
        'status': 'ok',
        'detector_loaded': detector is not None,
        'camera_active': camera is not None and is_capturing,
        'model_path': MODEL_PATH
    })

@app.route('/detect', methods=['GET'])
def get_detection():
    """Get current drowsiness detection result"""
    if detector is None:
        return jsonify({'error': 'Detector not initialized'}), 500
    
    if not is_capturing:
        return jsonify({'error': 'Camera not capturing'}), 500
    
    with result_lock:
        if current_result is None:
            return jsonify({
                'is_drowsy': False,
                'confidence': 0.0,
                'detections': []
            })
        return jsonify(current_result)

@app.route('/detect_simple', methods=['GET'])
def get_detection_simple():
    """Simplified endpoint"""
    if detector is None:
        return jsonify({'error': 'Detector not initialized'}), 500
    
    if not is_capturing:
        return jsonify({'error': 'Camera not capturing'}), 500
    
    with result_lock:
        if current_result is None:
            return jsonify({
                'is_drowsy': False,
                'confidence': 0.0
            })
        return jsonify({
            'is_drowsy': current_result.get('is_drowsy', False),
            'confidence': current_result.get('confidence', 0.0)
        })

if __name__ == '__main__':
    logger.info("Starting server in SAFE MODE (reduced CPU/memory usage)")
    
    init_detector()
    if detector is None:
        logger.warning("Starting server without detector")
    
    if not init_camera():
        logger.error("Failed to initialize camera - server will start but detection won't work")
    
    if camera is not None:
        capture_thread = threading.Thread(target=capture_and_detect, daemon=True)
        capture_thread.start()
        logger.info("Camera capture thread started")
    
    try:
        app.run(host='0.0.0.0', port=5000, debug=False, threaded=True)
    finally:
        is_capturing = False
        if capture_thread is not None:
            capture_thread.join(timeout=2)
        
        if camera is not None:
            if hasattr(camera, 'stop'):
                camera.stop()
        
        logger.info("Server stopped")

