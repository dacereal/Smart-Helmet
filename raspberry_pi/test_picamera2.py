#!/usr/bin/env python3
"""Test picamera2 directly"""

try:
    from picamera2 import Picamera2
    import numpy as np
    
    print("Testing picamera2...")
    
    # Initialize camera
    camera = Picamera2()
    print("✓ Picamera2 object created")
    
    # Configure camera
    config = camera.create_still_configuration(main={"size": (640, 480)})
    camera.configure(config)
    print("✓ Camera configured")
    
    # Start camera
    camera.start()
    print("✓ Camera started")
    
    # Wait a bit for camera to stabilize
    import time
    time.sleep(1)
    
    # Try to capture
    print("Attempting to capture frame...")
    frame = camera.capture_array()
    
    if frame is not None:
        print(f"✓ Successfully captured frame!")
        print(f"  Frame shape: {frame.shape}")
        print(f"  Frame dtype: {frame.dtype}")
        print(f"  Frame size: {frame.size}")
        print("\n✅ Pi Camera works with picamera2!")
        
        # Stop camera
        camera.stop()
        print("✓ Camera stopped")
    else:
        print("✗ Captured frame is None")
        camera.stop()
        
except ImportError as e:
    print(f"✗ Failed to import picamera2: {e}")
    print("Install with: pip3 install picamera2")
except Exception as e:
    print(f"✗ Error: {e}")
    import traceback
    traceback.print_exc()

