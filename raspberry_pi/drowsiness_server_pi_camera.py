#!/usr/bin/env python3
"""
Drowsiness Detection Service for Raspberry Pi 5
Uses ONNX Runtime for optimized inference
NOW WITH PI CAMERA SUPPORT - Captures frames directly from Pi Camera
"""

from flask import Flask, jsonify, Response
from flask_cors import CORS
import cv2
import numpy as np
import logging
import os
import threading
import time
import io

# GPIO for vibration motor control
try:
    from gpiozero import OutputDevice
    GPIO_AVAILABLE = True
except ImportError:
    try:
        import RPi.GPIO as GPIO
        GPIO_AVAILABLE = True
        GPIO_LEGACY = True
    except ImportError:
        GPIO_AVAILABLE = False
        logging.warning("GPIO libraries not available. Vibration motor will not work.")

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = Flask(__name__)
CORS(app)  # Enable CORS for Android app

# Try to use ultralytics for exact PyCharm compatibility, fallback to ONNX detector
USE_ULTRALYTICS = True  # Set to True to use ultralytics (matches PyCharm exactly)
try:
    from drowsiness_detector_ultralytics import DrowsinessDetectorUltralytics
    ULTRALYTICS_AVAILABLE = True
except ImportError:
    ULTRALYTICS_AVAILABLE = False
    logger.warning("ultralytics not available, using ONNX detector. Install with: pip install ultralytics")
    from drowsiness_detector import DrowsinessDetector

# Initialize detector
MODEL_PATH = os.path.join(os.path.dirname(__file__), 'models', 'my_model.onnx')
if not os.path.exists(MODEL_PATH):
    # Fallback to parent directory
    MODEL_PATH = os.path.join(os.path.dirname(__file__), '..', 'app', 'src', 'main', 'assets', 'my_model.onnx')

detector = None
camera = None
current_result = None
current_frame = None  # Latest frame for streaming
frame_lock = threading.Lock()  # Lock for frame access
result_lock = threading.Lock()
capture_thread = None
is_capturing = False

# Vibration motor control
VIBRATION_GPIO_PIN = 18
vibration_motor = None
vibration_timer_active = False


vibration_start_time = 0
vibration_is_active = False  # Track if vibration is currently running
DROWSINESS_THRESHOLD_MS = 1500  # 1.5 seconds before activating vibration
vibration_lock = threading.Lock()

def init_vibration_motor():
    """Initialize the vibration motor GPIO"""
    global vibration_motor
    if not GPIO_AVAILABLE:
        logger.warning("GPIO not available - vibration motor disabled")
        return False
    
    try:
        if 'GPIO_LEGACY' in globals() and GPIO_LEGACY:
            # Use RPi.GPIO (legacy)
            GPIO.setmode(GPIO.BCM)
            GPIO.setup(VIBRATION_GPIO_PIN, GPIO.OUT)
            GPIO.output(VIBRATION_GPIO_PIN, GPIO.LOW)
            logger.info(f"Vibration motor initialized on GPIO {VIBRATION_GPIO_PIN} (RPi.GPIO)")
        else:
            # Use gpiozero (modern)
            vibration_motor = OutputDevice(VIBRATION_GPIO_PIN, active_high=True, initial_value=False)
            logger.info(f"Vibration motor initialized on GPIO {VIBRATION_GPIO_PIN} (gpiozero)")
        return True
    except Exception as e:
        logger.error(f"Failed to initialize vibration motor: {e}")
        vibration_motor = None
        return False

def start_vibration():
    """Start the vibration motor"""
    global vibration_motor
    if not GPIO_AVAILABLE:
        logger.warning("GPIO not available - cannot start vibration")
        return
    
    try:
        if 'GPIO_LEGACY' in globals() and GPIO_LEGACY:
            GPIO.output(VIBRATION_GPIO_PIN, GPIO.HIGH)
            logger.info(f"✅ Vibration motor STARTED on GPIO {VIBRATION_GPIO_PIN} (HIGH)")
        else:
            if vibration_motor is not None:
                vibration_motor.on()
                logger.info(f"✅ Vibration motor STARTED on GPIO {VIBRATION_GPIO_PIN}")
            else:
                logger.error("Vibration motor device is None - not initialized")
    except Exception as e:
        logger.error(f"❌ Error starting vibration motor: {e}", exc_info=True)

def stop_vibration():
    """Stop the vibration motor"""
    global vibration_motor
    if not GPIO_AVAILABLE:
        return
    
    try:
        if 'GPIO_LEGACY' in globals() and GPIO_LEGACY:
            GPIO.output(VIBRATION_GPIO_PIN, GPIO.LOW)
            logger.info(f"🛑 Vibration motor STOPPED on GPIO {VIBRATION_GPIO_PIN} (LOW)")
        else:
            if vibration_motor is not None:
                vibration_motor.off()
                logger.info(f"🛑 Vibration motor STOPPED on GPIO {VIBRATION_GPIO_PIN}")
    except Exception as e:
        logger.error(f"❌ Error stopping vibration motor: {e}", exc_info=True)

def handle_vibration_control(is_drowsy):
    """Handle vibration motor control with 1.5 second delay"""
    global vibration_timer_active, vibration_start_time, vibration_is_active
    
    with vibration_lock:
        current_time = time.time() * 1000  # Convert to milliseconds
        
        if is_drowsy:
            if not vibration_timer_active:
                # Start the 1.5-second timer
                vibration_timer_active = True
                vibration_start_time = current_time
                vibration_is_active = False
                logger.info(f"⏱️ Vibration timer started - waiting 1.5 seconds (is_drowsy={is_drowsy})")
            elif not vibration_is_active:
                # Timer is already active, check if 1.5 seconds have passed
                elapsed = current_time - vibration_start_time
                if elapsed >= DROWSINESS_THRESHOLD_MS:
                    # 1.5 seconds have passed, start vibration (only once)
                    logger.info(f"⏰ 1.5 seconds elapsed ({elapsed:.0f}ms) - ACTIVATING VIBRATION MOTOR")
                    start_vibration()
                    vibration_is_active = True
            # If vibration_is_active is True, keep it running (don't do anything)
        else:
            # Eyes are open - immediately stop vibration and cancel timer
            if vibration_timer_active or vibration_is_active:
                if vibration_timer_active:
                    logger.info("👁️ Eyes opened - cancelling vibration timer")
                    vibration_timer_active = False
                    vibration_start_time = 0
                if vibration_is_active:
                    logger.info("👁️ Eyes opened - stopping vibration motor")
                    vibration_is_active = False
                    stop_vibration()

def init_detector():
    """Initialize the drowsiness detector"""
    global detector
    try:
        if not os.path.exists(MODEL_PATH):
            logger.error(f"Model file not found at: {MODEL_PATH}")
            logger.error("Please ensure my_model.onnx is in the models/ directory")
            return
        
        # Use ultralytics if available and requested (guarantees PyCharm compatibility)
        if USE_ULTRALYTICS and ULTRALYTICS_AVAILABLE:
            detector = DrowsinessDetectorUltralytics(MODEL_PATH)
            logger.info("Drowsiness detector initialized with ultralytics (PyCharm compatible)")
        else:
            detector = DrowsinessDetector(MODEL_PATH)
            logger.info("Drowsiness detector initialized with ONNX runtime")
            
    except Exception as e:
        logger.error(f"Failed to initialize detector: {e}")
        detector = None

def init_camera():
    """Initialize Raspberry Pi Camera"""
    global camera
    try:
        # Try to initialize Pi Camera using picamera2 (modern approach) - REQUIRED for Pi Camera
        try:
            from picamera2 import Picamera2
            camera = Picamera2()
            # Configure camera - use still configuration for capture
            camera_config = camera.create_still_configuration(main={"size": (640, 480)})
            camera.configure(camera_config)
            camera.start()
            
            # Test capture
            test_frame = camera.capture_array()
            if test_frame is not None and test_frame.size > 0:
                logger.info("Pi Camera initialized successfully (Picamera2)")
                return True
            else:
                camera.stop()
                camera = None
                raise Exception("Picamera2 opened but failed to capture test frame")
                
        except ImportError:
            logger.error("Picamera2 not available - this is REQUIRED for Pi Camera on modern Pi OS")
            logger.error("Install with: pip3 install picamera2")
            logger.error("Falling back to OpenCV (may not work)")
        except Exception as e:
            logger.error(f"Picamera2 initialization failed: {e}")
            if camera is not None:
                try:
                    camera.stop()
                except:
                    pass
                camera = None
        
        # Fallback to OpenCV - NOTE: This usually doesn't work with Pi Camera on modern Pi OS
        # Pi Camera requires libcamera/picamera2, not V4L2
        logger.warning("Attempting OpenCV fallback (likely to fail on modern Pi OS)")
        for video_idx in [0, 1, 2]:
            try:
                test_cap = cv2.VideoCapture(video_idx)
                if test_cap.isOpened():
                    # Try to read a test frame to verify it works
                    ret, test_frame = test_cap.read()
                    if ret and test_frame is not None and test_frame.size > 0:
                        # Success - create permanent capture
                        camera = cv2.VideoCapture(video_idx)
                        camera.set(cv2.CAP_PROP_FRAME_WIDTH, 640)
                        camera.set(cv2.CAP_PROP_FRAME_HEIGHT, 480)
                        camera.set(cv2.CAP_PROP_FPS, 10)
                        logger.info(f"Camera initialized successfully (OpenCV) on /dev/video{video_idx}")
                        test_cap.release()
                        return True
                    test_cap.release()
            except Exception as e:
                logger.debug(f"Failed to open /dev/video{video_idx}: {e}")
        
        logger.error("="*60)
        logger.error("FAILED TO INITIALIZE CAMERA")
        logger.error("="*60)
        logger.error("Pi Camera requires picamera2 on modern Pi OS")
        logger.error("OpenCV/V4L2 access doesn't work with Pi Camera anymore")
        logger.error("")
        logger.error("Solutions:")
        logger.error("1. Install picamera2: pip3 install picamera2")
        logger.error("2. Check camera is physically connected")
        logger.error("3. Check camera cable is properly seated")
        logger.error("4. Try: libcamera-hello (if available)")
        logger.error("="*60)
        return False
            
    except Exception as e:
        logger.error(f"Failed to initialize camera: {e}")
        return False

def capture_and_detect():
    """Continuously capture frames and run detection"""
    global current_result, current_frame, is_capturing
    
    is_capturing = True
    logger.info("Starting camera capture thread")
    frame_counter = 0
    
    while is_capturing:
        try:
            # Capture frame from Pi Camera
            frame = None
            
            if hasattr(camera, 'capture_array'):
                # Picamera2 API
                try:
                    frame = camera.capture_array()
                    # Convert RGB to BGR for OpenCV (if needed)
                    frame = cv2.cvtColor(frame, cv2.COLOR_RGB2BGR)
                except Exception as e:
                    logger.warning(f"Failed to capture frame with Picamera2: {e}")
                    time.sleep(0.1)
                    continue
            else:
                # OpenCV VideoCapture
                ret, frame = camera.read()
                if not ret or frame is None:
                    logger.warning("Failed to capture frame with OpenCV")
                    time.sleep(0.1)
                    continue
            
            if frame is None:
                continue
            
            # Store latest frame for streaming (thread-safe)
            with frame_lock:
                current_frame = frame.copy()
            
            # Run detection on EVERY frame for maximum accuracy (as requested)
            # This ensures accurate detection even with lower frame rates
            if detector is not None:
                try:
                    result = detector.get_detection_result(frame)
                    
                    # Update current result (thread-safe)
                    with result_lock:
                        current_result = result
                    
                    # Control vibration motor based on detection result
                    is_drowsy = result.get('is_drowsy', False)
                    confidence = result.get('confidence', 0.0)
                    
                    frame_counter += 1
                    
                    # Log detection result periodically (every 25 frames = ~5 seconds at 5 FPS)
                    if frame_counter % 25 == 0:
                        logger.info(f"Detection status: is_drowsy={is_drowsy}, confidence={confidence:.2f}, frame={frame_counter}")
                    
                    # Control vibration motor
                    handle_vibration_control(is_drowsy)
                except Exception as e:
                    logger.error(f"Detection error on frame: {e}")
                    # Keep previous result if detection fails
            else:
                # No detector, set default result
                with result_lock:
                    current_result = {
                        'is_drowsy': False,
                        'confidence': 0.0,
                        'detections': []
                    }
                # Stop vibration if no detector
                handle_vibration_control(False)
            
            # Optimized for official Pi 5 power supply (27W):
            # 5 FPS = smooth streaming + accurate detection (every frame)
            # With proper power, this runs smoothly without crashes
            time.sleep(0.2)  # 0.2 seconds = 5 FPS (optimal with official power supply)
            
        except Exception as e:
            logger.error(f"Error in capture loop: {e}", exc_info=True)
            time.sleep(0.5)
    
    logger.info("Camera capture thread stopped")

@app.route('/stream', methods=['GET'])
def stream():
    """MJPEG video stream endpoint"""
    def generate():
        """Generator function to stream frames"""
        while True:
            try:
                # Get latest frame (thread-safe)
                with frame_lock:
                    if current_frame is not None:
                        frame = current_frame.copy()
                    else:
                        # Create a black frame if no frame available
                        frame = np.zeros((480, 640, 3), dtype=np.uint8)
                
                # Encode frame as JPEG (good quality for viewing)
                encode_param = [int(cv2.IMWRITE_JPEG_QUALITY), 80]  # 80% quality (good balance with proper power)
                result, jpeg_frame = cv2.imencode('.jpg', frame, encode_param)
                
                if result:
                    # MJPEG stream format
                    yield (b'--frame\r\n'
                           b'Content-Type: image/jpeg\r\n\r\n' + 
                           jpeg_frame.tobytes() + b'\r\n')
                else:
                    logger.warning("Failed to encode frame as JPEG")
                    time.sleep(0.2)
                
                # Stream at 5 FPS to match capture rate (smooth viewing with official power supply)
                time.sleep(0.2)
                
            except Exception as e:
                logger.error(f"Error in stream generator: {e}")
                time.sleep(0.5)
    
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
    """
    Get current drowsiness detection result from Pi Camera
    
    Response:
    {
        "is_drowsy": bool,
        "confidence": float,
        "detections": [...]
    }
    """
    if detector is None:
        return jsonify({
            'error': 'Detector not initialized'
        }), 500
    
    if not is_capturing:
        return jsonify({
            'error': 'Camera not capturing'
        }), 500
    
    # Get current result (thread-safe)
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
    """
    Simplified endpoint that returns only is_drowsy boolean
    
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
    
    if not is_capturing:
        return jsonify({
            'error': 'Camera not capturing'
        }), 500
    
    # Get current result (thread-safe)
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

@app.route('/test_vibration', methods=['GET'])
def test_vibration_endpoint():
    """
    Test endpoint to manually trigger vibration motor
    Useful for debugging wiring issues
    """
    try:
        if not GPIO_AVAILABLE:
            return jsonify({
                'error': 'GPIO not available',
                'message': 'Install gpiozero: pip install gpiozero'
            }), 500
        
        # Start vibration for 2 seconds
        logger.info("Manual vibration test - starting for 2 seconds")
        start_vibration()
        time.sleep(2)
        stop_vibration()
        
        return jsonify({
            'success': True,
            'message': 'Vibration test completed - motor should have vibrated for 2 seconds',
            'gpio_pin': VIBRATION_GPIO_PIN
        })
    except Exception as e:
        logger.error(f"Error in vibration test: {e}", exc_info=True)
        return jsonify({
            'error': str(e),
            'gpio_pin': VIBRATION_GPIO_PIN
        }), 500

@app.route('/vibration_status', methods=['GET'])
def get_vibration_status():
    """
    Get current vibration and detection status for debugging
    """
    with vibration_lock:
        with result_lock:
            return jsonify({
                'vibration_timer_active': vibration_timer_active,
                'vibration_is_active': vibration_is_active,
                'vibration_start_time': vibration_start_time,
                'current_time_ms': time.time() * 1000,
                'elapsed_ms': (time.time() * 1000) - vibration_start_time if vibration_timer_active else 0,
                'threshold_ms': DROWSINESS_THRESHOLD_MS,
                'gpio_pin': VIBRATION_GPIO_PIN,
                'current_detection': current_result if current_result else None,
                'gpio_available': GPIO_AVAILABLE
            })

if __name__ == '__main__':
    # Initialize detector
    init_detector()
    
    if detector is None:
        logger.warning("Starting server without detector - endpoints will return errors")
    
    # Initialize camera
    if not init_camera():
        logger.error("Failed to initialize camera - server will start but detection won't work")
    
    # Initialize vibration motor
    if init_vibration_motor():
        logger.info("Vibration motor ready")
    else:
        logger.warning("Vibration motor not available - continuing without vibration")
    
    # Start camera capture thread
    if camera is not None:
        capture_thread = threading.Thread(target=capture_and_detect, daemon=True)
        capture_thread.start()
        logger.info("Camera capture thread started")
    
    # Run server
    # Use 0.0.0.0 to allow connections from other devices on the network
    try:
        app.run(host='0.0.0.0', port=5000, debug=False, threaded=True)
    finally:
        # Cleanup
        is_capturing = False
        if capture_thread is not None:
            capture_thread.join(timeout=2)
        
        # Stop vibration motor
        stop_vibration()
        
        # Cleanup GPIO
        if GPIO_AVAILABLE:
            try:
                if 'GPIO_LEGACY' in globals() and GPIO_LEGACY:
                    GPIO.cleanup()
                elif vibration_motor is not None:
                    vibration_motor.close()
            except Exception as e:
                logger.error(f"Error cleaning up GPIO: {e}")
        
        if camera is not None:
            if hasattr(camera, 'stop'):
                camera.stop()
            else:
                camera.release()
        
        logger.info("Server stopped")

