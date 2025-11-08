# Simple Usage: Android App + Raspberry Pi with Pi Camera

## Setup Overview

**What You Have:**
- ✅ Raspberry Pi 5
- ✅ Raspberry Pi Camera (hardware camera connected to Pi)
- ✅ Android App

**How It Works:**
1. **Pi captures frames** from its own camera
2. **Pi runs drowsiness detection** using ONNX model
3. **Android app queries Pi** for detection results (GET request)
4. **App displays status** (Alert/Drowsy)

---

## Quick Setup

### Step 1: Enable Pi Camera

**On Raspberry Pi:**

```bash
sudo raspi-config
```

1. **Interface Options** → **Camera** → **Enable**
2. Reboot: `sudo reboot`

**Verify camera:**
```bash
libcamera-hello --list-cameras
```

---

### Step 2: Install Pi Camera Libraries

**On Raspberry Pi:**

```bash
cd ~/smart_helmet_pi
source venv/bin/activate

# Install picamera2
pip3 install picamera2

# Or if using older Pi OS:
pip3 install picamera
```

---

### Step 3: Use Pi Camera Server

**Copy the new server file:**

```bash
cd ~/smart_helmet_pi

# Backup old server
mv drowsiness_server.py drowsiness_server_old.py

# Use Pi Camera version
# (Already created: drowsiness_server_pi_camera.py)
```

**Or rename it:**
```bash
mv drowsiness_server_pi_camera.py drowsiness_server.py
```

---

### Step 4: Start Pi Camera Server

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

### Step 5: Build & Run Android App

1. **Open project** in Android Studio
2. **Build & Install** on your Android device
3. **App automatically:**
   - Connects to Pi server
   - Queries Pi every 200ms for detection results
   - Updates UI with status

---

## How It Works

```
┌─────────────┐
│ Pi Camera   │ ──┐
│ (Hardware)  │   │ Captures frames
└─────────────┘   │
                  ▼
           ┌─────────────┐
           │ Raspberry   │ ──────────┐
           │    Pi 5     │           │
           │             │           │ Detection Results
           │ ┌─────────┐ │           │ {"is_drowsy": true}
           │ │  ONNX   │ │           │
           │ │  Model  │ │           │
           │ └─────────┘ │           │
           │             │           │
           │ Runs        │           │
           │ Detection   │           │
           └─────────────┘           │
                  ▲                  │
                  │                  │
                  │ GET /detect_simple
                  │                  │
           ┌──────┴──────────────────┴──────┐
           │   Android App                  │
           │                                │
           │ Queries Pi every 200ms         │
           │ Displays status                │
           └────────────────────────────────┘
```

---

## Usage

### 1. Start Pi Server

**On Raspberry Pi:**
```bash
cd ~/smart_helmet_pi
source venv/bin/activate
python3 drowsiness_server_pi_camera.py
```

**Keep this running!**

### 2. Open Android App

1. Launch Smart Helmet app
2. Login
3. App automatically starts querying Pi
4. You'll see status updates:
   - **"Driver Status: Alert"** (green) = Awake
   - **"Driver Status: Drowsy"** (red) = Drowsy

### 3. That's It!

- Pi captures frames from its camera
- Pi runs detection
- App queries Pi for results
- Status updates automatically

---

## Configuration

### Change Query Rate

**In `Dashboard.java`:**
```java
// Line ~95: Adjust query interval
private static final long PI_QUERY_INTERVAL_MS = 200; // 200ms = ~5 FPS
```

**Faster:** Lower number (100ms = ~10 FPS)
**Slower:** Higher number (500ms = 2 FPS)

### Pi Server URL

**In `Dashboard.java`:**
```java
// Line 91: Pi server URL
private static final String PI_SERVER_URL = "http://192.168.43.151:5000";
```

Update if your Pi has different IP.

---

## Auto-Start Pi Server

**Set up as systemd service:**

```bash
# On Pi
cd ~/smart_helmet_pi
sudo cp drowsiness.service /etc/systemd/system/

# Edit service file to use Pi Camera version
sudo nano /etc/systemd/system/drowsiness.service
```

**Change ExecStart line:**
```ini
ExecStart=/home/smarthelmet/smart_helmet_pi/venv/bin/python3 /home/smarthelmet/smart_helmet_pi/drowsiness_server_pi_camera.py
```

**Enable and start:**
```bash
sudo systemctl enable drowsiness.service
sudo systemctl start drowsiness.service

# Check status
sudo systemctl status drowsiness.service
```

---

## Testing

### Test Pi Camera

```bash
# Test camera directly
libcamera-hello

# Or test with Python
python3 -c "from picamera2 import Picamera2; cam = Picamera2(); cam.start(); print('Camera OK!'); cam.stop()"
```

### Test Pi Server

```bash
# From Pi or Windows
curl http://192.168.43.151:5000/health

# Should return:
{"status": "ok", "detector_loaded": true, "camera_active": true}
```

### Test Detection Endpoint

```bash
curl http://192.168.43.151:5000/detect_simple

# Should return:
{"is_drowsy": false, "confidence": 0.0}
```

---

## Troubleshooting

### Camera Not Detected

```bash
# Check if camera is enabled
vcgencmd get_camera

# Should show: supported=1 detected=1

# Enable if needed
sudo raspi-config
# Interface Options → Camera → Enable
sudo reboot
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

### App Can't Connect

- Check Pi server is running
- Check both devices on same WiFi network
- Check Pi IP address is correct in app
- Test from Android: `curl http://192.168.43.151:5000/health`

---

## Performance

- **Frame Rate**: ~10 FPS (adjustable in Pi code)
- **Query Rate**: ~5 FPS (200ms interval, adjustable in app)
- **Detection Latency**: ~50-100ms per frame
- **CPU Usage**: ~30-50% on Pi 5
- **Memory**: ~200-300MB

---

## Summary

✅ **Pi Camera** captures frames  
✅ **Pi** runs detection  
✅ **Android app** queries Pi  
✅ **No ESP32 needed!**  
✅ **No sending frames from app!**  

Just **Pi + Pi Camera + Android app** - Simple and clean! 🎉

