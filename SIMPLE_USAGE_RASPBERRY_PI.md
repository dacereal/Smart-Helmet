# Simple Usage: App + Raspberry Pi Setup

## Quick Start - Just Plug and Go!

### What You Need:
1. **Raspberry Pi 5** - already set up with drowsiness detection server
2. **ESP32 Camera Device** - your helmet camera
3. **Android App** - Smart Helmet app
4. **WiFi Network** - all devices on same network

---

## Simple Usage Steps

### Step 1: Power On Raspberry Pi
- Connect Pi to power (USB-C or power supply)
- Wait ~30 seconds for Pi to boot
- Server should auto-start if configured, or start manually

### Step 2: Start Pi Server (if not auto-starting)

**SSH into Pi:**
```bash
ssh smarthelmet@192.168.43.151
```

**Start server:**
```bash
cd ~/smart_helmet_pi
source venv/bin/activate
python3 drowsiness_server.py
```

Keep this running or set it up as a service to auto-start.

### Step 3: Plug In ESP32 Device
- Connect ESP32 to USB power (or external power)
- ESP32 automatically connects to WiFi
- ESP32 starts streaming camera feed

### Step 4: Open Android App
- Launch Smart Helmet app on your Android device
- Login with your Firebase credentials
- App automatically:
  - Connects to ESP32 camera stream (via WiFi)
  - Connects to Raspberry Pi server
  - Starts drowsiness detection

**That's it!** 🎉

---

## How It Works (Simplified)

```
┌─────────────┐         WiFi          ┌─────────────┐         WiFi          ┌─────────────┐
│   ESP32     │ ────────────────────▶ │   Android   │ ────────────────────▶ │ Raspberry   │
│   Camera    │  MJPEG Stream          │    App      │  Camera Frames        │     Pi 5    │
│             │                        │             │  (Base64 Images)      │             │
│  ┌──────┐   │                        │  ┌────────┐│                      │  ┌────────┐│
│  │Camera│   │                        │  │Receive ││  HTTP POST /detect   │  │  ONNX   ││
│  └──────┘   │                        │  │ Frames ││ ────────────────────▶ │  │ Model   ││
│             │                        │  └────────┘│                      │  │Inference ││
│ Streams to: │                        │  ┌────────┐│  JSON Response       │  └────────┘│
│ Port 81     │                        │  │  Send  ││ ◀───────────────────  │             │
└─────────────┘                        │  │  to Pi ││   {"is_drowsy":true}  │  Port 5000 │
                                       │  └────────┘│                      │             │
                                       │  ┌────────┐│                      │  Server    │
                                       │  │ Display ││                      │  Running   │
                                       │  │ Results ││                      │             │
                                       │  └────────┘│                      └─────────────┘
                                       └─────────────┘
```

### Flow:
1. **ESP32** captures camera frames → streams MJPEG to Android app
2. **Android app** receives frames → converts to JPEG → base64 encodes
3. **Android app** sends image to Raspberry Pi via HTTP POST
4. **Raspberry Pi** runs ONNX model inference → returns detection result
5. **Android app** receives result → updates UI → triggers alarms

---

## Daily Usage (After Initial Setup)

### Option A: Auto-Start Pi Server (Recommended)

**Set up Pi server to auto-start on boot:**

```bash
# On Pi (one-time setup)
cd ~/smart_helmet_pi
sudo cp drowsiness.service /etc/systemd/system/
sudo systemctl enable drowsiness.service
sudo systemctl start drowsiness.service
```

**Now:**
- Just power on Pi
- Server starts automatically
- Wait ~10 seconds for server to be ready

**Check if running:**
```bash
# From Windows PowerShell
curl http://192.168.43.151:5000/health
```

---

### Option B: Manual Start (When Needed)

**When you want to use the system:**
1. SSH into Pi: `ssh smarthelmet@192.168.43.151`
2. Start server: `cd ~/smart_helmet_pi && source venv/bin/activate && python3 drowsiness_server.py`
3. Keep SSH session open

---

## Current Configuration

### App Settings (Dashboard.java):

```java
// Line 90: Using Raspberry Pi for detection
private static final boolean USE_PI_DETECTION = true;  // ✅ Already set!

// Line 91: Pi server URL
private static final String PI_SERVER_URL = "http://192.168.43.151:5000";

// Line 107: ESP32 IP address (update with your ESP32's IP)
private static String ESP32_IP = "192.168.1.XXX";  // ← Update this!
```

**What you need to do:**
- ✅ `USE_PI_DETECTION = true` (already correct!)
- ✅ Pi server URL is set (already correct!)
- ⚠️ Update ESP32_IP with your ESP32's actual IP address

---

## Finding ESP32 IP Address

### Method 1: ESP32 Serial Monitor
- Open Arduino IDE
- Connect ESP32 via USB
- Open Serial Monitor (115200 baud)
- Look for output:
  ```
  WiFi connected!
  IP address: 192.168.1.XXX
  MJPEG Stream URL: http://192.168.1.XXX:81/stream
  ```

### Method 2: Router Admin Panel
- Access your router settings (usually `192.168.1.1`)
- Check connected devices
- Find ESP32 by MAC address or device name

### Method 3: Network Scanner App
- Use Fing or similar network scanner app on Android
- Scan your WiFi network
- Find ESP32 device

---

## Complete Usage Checklist

### Initial Setup (One Time):
- [ ] Raspberry Pi set up with server code
- [ ] Pi server dependencies installed
- [ ] Model file (`my_model.onnx`) in Pi's `models/` folder
- [ ] Pi server tested and working
- [ ] ESP32 code uploaded with WiFi credentials
- [ ] ESP32 IP address noted
- [ ] Android app configured with:
  - [ ] `USE_PI_DETECTION = true` ✅
  - [ ] Pi server URL: `http://192.168.43.151:5000` ✅
  - [ ] ESP32 IP address set
- [ ] Android app built and installed

### Daily Usage:
- [ ] Power on Raspberry Pi
- [ ] Wait for Pi server to start (check health endpoint)
- [ ] Plug in ESP32 device
- [ ] Wait for ESP32 to connect to WiFi
- [ ] Open Android app
- [ ] Login to Smart Helmet app
- [ ] Camera preview appears
- [ ] Drowsiness detection works!

---

## What You'll See

### When Everything Works:

1. **Android App Startup:**
   - Toast: "Connected to Raspberry Pi" ✅
   - Camera preview from ESP32 appears
   - Detection boxes appear (green for alert, red for drowsy)

2. **Pi Server Logs (SSH session):**
   ```
   INFO:werkzeug:192.168.43.X - - [31/Oct/2025 21:XX:XX] "POST /detect_simple HTTP/1.1" 200 -
   ```

3. **Android Logcat:**
   ```
   D/Dashboard: Frame #X Pi ML result: AWAKE/DROWSY
   D/PiDrowsinessDetector: Response code: 200
   ```

4. **UI Updates:**
   - Status shows "Driver Status: Alert" (green) or "Driver Status: Drowsy" (red)
   - Detection boxes on camera preview
   - Alarm triggers if drowsy for 2+ seconds

---

## Troubleshooting

### ❌ "Pi server not reachable"

**Quick Fix:**
1. Check Pi server is running:
   ```bash
   curl http://192.168.43.151:5000/health
   ```
2. If no response, SSH into Pi and start server:
   ```bash
   ssh smarthelmet@192.168.43.151
   cd ~/smart_helmet_pi
   source venv/bin/activate
   python3 drowsiness_server.py
   ```

### ❌ No Camera Preview

**Check:**
1. ESP32 powered on?
2. ESP32 connected to WiFi? (Check Serial Monitor)
3. ESP32 IP correct in app?
4. Test ESP32 stream in browser: `http://ESP32_IP:81/stream`

### ❌ Slow Detection

**Possible causes:**
- Network latency
- Pi CPU overload

**Solutions:**
- Check WiFi signal strength
- Monitor Pi CPU: `htop` on Pi
- Reduce image quality in app (PiDrowsinessDetector.java)

---

## Quick Reference

### Pi Server Commands:
```bash
# Start server
cd ~/smart_helmet_pi && source venv/bin/activate && python3 drowsiness_server.py

# Check if running
curl http://192.168.43.151:5000/health

# Stop server
# Press Ctrl+C in SSH session

# Auto-start on boot
sudo systemctl enable drowsiness.service
sudo systemctl start drowsiness.service
```

### App Configuration:
- `USE_PI_DETECTION = true` ← Using Raspberry Pi ✅
- Pi URL: `http://192.168.43.151:5000`
- ESP32 IP: `192.168.1.XXX` ← Update this!

---

## Summary: Simple Daily Usage

**After initial setup:**

1. **Power on Pi** → Server starts (auto or manual)
2. **Plug in ESP32** → Connects to WiFi
3. **Open Android app** → Everything connects automatically!

**That's it!** Just 3 steps: Power Pi → Plug ESP32 → Open App 🎉

---

Need help? Check the full guide: `USAGE_GUIDE_RASPBERRY_PI.md`

