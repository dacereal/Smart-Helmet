#!/usr/bin/env python3
"""Quick test script to check camera availability"""

import cv2
import sys

print("Testing camera access...")

# Try different video devices
for video_idx in range(4):
    print(f"\nTrying /dev/video{video_idx}...")
    cap = cv2.VideoCapture(video_idx)
    
    if cap.isOpened():
        print(f"  ✓ /dev/video{video_idx} opened successfully")
        
        # Try to read a frame
        ret, frame = cap.read()
        if ret and frame is not None:
            print(f"  ✓ Successfully captured frame: {frame.shape}")
            print(f"    Frame dimensions: {frame.shape[1]}x{frame.shape[0]}")
            cap.release()
            print(f"\n✅ Camera works on /dev/video{video_idx}!")
            sys.exit(0)
        else:
            print(f"  ✗ Failed to read frame")
            cap.release()
    else:
        print(f"  ✗ Failed to open")

print("\n❌ No working camera found!")
print("\nTroubleshooting:")
print("1. Check if camera is physically connected")
print("2. Check camera cable connection")
print("3. Try: ls -la /dev/video*")
print("4. Check if camera is enabled in system settings")

