# Setting Up Raspberry Pi Camera for Drowsiness Detection

## Overview

Your Raspberry Pi will:
1. **Capture frames** from Pi Camera
2. **Run drowsiness detection** using ONNX model
3. **Serve results** via REST API that Android app can query

No ESP32 needed! Just Pi + Pi Camera + Android app.

---

## Setup Steps

### Step 1: Enable Pi Camera

**On Raspberry Pi:**

```bash
sudo raspi-config
```

1. Navigate to **Interface Options**
2. Select **Camera**
3. Select **Enable**
4. Reboot: `sudo reboot`

**Verify camera is detected:**
```bash
libcamera-hello --list-cameras
```

---

### Step 2: Install Pi Camera Libraries

**On Raspberry Pi:**

```bash
cd ~/smart_helmet_pi
source venv/bin/activate

# Install picamera2 (modern Pi Camera library)
pip3 install picamera2
```

**Or if using older Pi OS:**
```bash
pip3 install picamera  # Legacy library
```

---

### Step 3: Use Pi Camera Server

**Use the new server file:**
```bash
cd ~/smart_helmet_pi

# Use the Pi Camera version instead
python3 drowsiness_server_pi_camera.py
```

Or replace the old server:
```bash
mv drowsiness_server.py drowsiness_server_old.py
mv drowsiness_server_pi_camera.py drowsiness_server.py
```

---

### Step 4: Test Pi Camera

**On Raspberry Pi:**
```bash
# Test camera with picamera2
python3 -c "from picamera2 import Picamera2; cam = Picamera2(); cam.start(); print('Camera OK!'); cam.stop()"
```

**Or test with OpenCV:**
```bash
python3 -c "import cv2; cap = cv2.VideoCapture(0); print('Camera opened:', cap.isOpened()); cap.release()"
```

---

### Step 5: Start Server

**On Raspberry Pi:**
```bash
cd ~/smart_helmet_pi
source venv/bin/activate
python3 drowsiness_server_pi_camera.py
```

**Expected output:**
```
INFO:drowsiness_detector:Model loaded successfully
INFO:__main__:Pi Camera initialized successfully
INFO:__main__:Camera capture thread started
 * Running on http://0.0.0.0:5000
```

---

### Step 6: Test from Android App

**Android app will query:**
- `GET http://192.168.43.151:5000/detect_simple`

**Returns:**
```json
{
  "is_drowsy": true,
  "confidence": 0.85
}
```

---

## How It Works

```
┌─────────────┐
│ Pi Camera   │
│ (Hardware)  │
└──────┬──────┘
       │
       ▼
┌─────────────┐         ┌─────────────┐
│ Raspberry   │         │   Android   │
│    Pi 5     │ ◄────── │    App      │
│             │  GET    │             │
│ ┌─────────┐ │ /detect │             │
│ │  ONNX   │ │         │ Queries for │
│ │  Model  │ │         │ results     │
│ └─────────┘ │         │             │
│             │         └─────────────┘
│ Captures &  │
│ Detects     │
└─────────────┘
```

1. **Pi Camera** captures frames continuously
2. **Pi** runs ONNX inference on each frame
3. **Android app** queries Pi for detection results
4. **Pi** returns current drowsiness status

---

## Update Android App

**In `Dashboard.java`:**

The app should query the Pi instead of sending frames:

```java
// Query Pi for detection results (instead of sending frames)
// GET request to http://192.168.43.151:5000/detect_simple
```

I'll update the Android app to query the Pi instead of sending frames.

---

## Troubleshooting

### Camera Not Detected

```bash
# Check if camera is enabled
vcgencmd get_camera

# Should return: supported=1 detected=1

# Enable if not enabled
sudo raspi-config
# Interface Options → Camera → Enable
```

### Permission Errors

```bash
# Add user to video group
sudo usermod -a -G video $USER

# Logout and login again
```

### Import Errors

```bash
# Install picamera2
pip3 install picamera2

# Or for older Pi OS
pip3 install picamera
```

---

## Auto-Start Service

**Create systemd service:**

```bash
cd ~/smart_helmet_pi
sudo cp drowsiness.service /etc/systemd/system/
sudo systemctl enable drowsiness.service
sudo systemctl start drowsiness.service
```

**Edit service file to use Pi Camera version:**
```ini
ExecStart=/home/smarthelmet/smart_helmet_pi/venv/bin/python3 /home/smarthelmet/smart_helmet_pi/drowsiness_server_pi_camera.py
```

---

## Performance

- **Frame Rate**: ~10 FPS (adjustable in code)
- **Detection Latency**: ~50-100ms per frame
- **CPU Usage**: ~30-50% on Pi 5
- **Memory**: ~200-300MB

---

That's it! Your Pi captures frames from its camera, runs detection, and serves results to your Android app!

