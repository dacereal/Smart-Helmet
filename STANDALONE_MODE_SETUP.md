# Standalone Mode: Android App + ESP32 Device (No Raspberry Pi)

## Overview

Use the Smart Helmet system with **just the Android app and ESP32 device** - no Raspberry Pi needed! The app runs drowsiness detection locally using TensorFlow Lite.

## Setup Steps

### Step 1: Configure Android App for Standalone Mode

**Edit `app/src/main/java/com/botsquad/smarthelmet/Dashboard.java`:**

```java
// Line 89-91: Switch to local detection
private static final boolean USE_PI_DETECTION = false;  // ← Change to false
private static final String PI_SERVER_URL = "http://192.168.43.151:5000";  // Not used in standalone mode

// Line 107-108: Set your ESP32 IP address
private static String ESP32_IP = "192.168.1.XXX";  // ← Change XXX to your ESP32's IP
private static int MJPEG_STREAM_PORT = 81;
```

**Steps:**
1. Change `USE_PI_DETECTION = false`
2. Update `ESP32_IP` with your ESP32's actual IP address
3. Rebuild the app

---

### Step 2: Configure ESP32 Device

**Upload ESP32 Code:**

1. **Open Arduino IDE** or your ESP32 development environment

2. **Upload `esp32_smart_helmet.ino`** to your ESP32:
   - Update WiFi credentials in the code:
     ```cpp
     const char* ssid = "YourWiFiName";
     const char* password = "YourWiFiPassword";
     ```
   - Or use `CameraWebServer` if you prefer

3. **Connect ESP32 to power** (USB or external power)

4. **Note the IP address** shown in Serial Monitor:
   ```
   WiFi connected!
   IP address: 192.168.1.XXX
   MJPEG Stream URL: http://192.168.1.XXX:81/stream
   ```

---

### Step 3: Update App with ESP32 IP

**In `Dashboard.java`, update the IP:**

```java
private static String ESP32_IP = "192.168.1.XXX";  // Use the IP from Serial Monitor
```

**Or configure in app:**
- The app has a device pairing feature - you can set the IP address there
- Go to settings/device pairing and enter the ESP32 IP

---

### Step 4: Build and Run App

1. **Rebuild the app** in Android Studio
2. **Install on your Android device**
3. **Connect both ESP32 and Android device to the same WiFi network**

---

### Step 5: Use the App

1. **Open Smart Helmet app** on Android
2. **Login** with Firebase credentials
3. **App will automatically:**
   - Load TFLite model locally (you'll see "AI model loaded successfully")
   - Connect to ESP32 camera stream
   - Start drowsiness detection

4. **What you'll see:**
   - Camera preview from ESP32
   - Detection boxes (green for alert, red for drowsy)
   - Status updates in real-time

---

## How It Works

```
┌─────────────┐      WiFi      ┌─────────────┐
│  ESP32      │ ──────────────▶│  Android    │
│  Camera     │  MJPEG Stream  │  App        │
│  Device     │                │             │
│             │                │  ┌─────────┐│
│  ┌──────┐   │                │  │ TFLite ││
│  │Camera│   │                │  │ Model  ││
│  └──────┘   │                │  └─────────┘│
│             │                │  ┌─────────┐│
│ Streams to: │                │  │Detection││
│ Port 81     │                │  │ Engine  ││
└─────────────┘                │  └─────────┘│
                                └─────────────┘
```

1. **ESP32** captures camera frames
2. **Streams MJPEG** to Android app via WiFi
3. **Android app** receives frames
4. **TFLite model** runs inference locally on Android
5. **Results** displayed in app UI

---

## Configuration

### ESP32 IP Address

**Option 1: Hard-code in Dashboard.java**
```java
private static String ESP32_IP = "192.168.1.100";  // Your ESP32 IP
```

**Option 2: Use Device Pairing Feature**
- App has built-in device pairing
- Settings → Device Pairing
- Enter ESP32 IP and port (81)

### ESP32 WiFi Setup

**In `esp32_smart_helmet.ino`:**
```cpp
const char* ssid = "YourWiFiName";
const char* password = "YourWiFiPassword";
```

**For static IP (optional):**
```cpp
// Add after WiFi.begin()
IPAddress local_IP(192, 168, 1, 100);  // Your desired IP
IPAddress gateway(192, 168, 1, 1);
IPAddress subnet(255, 255, 255, 0);
WiFi.config(local_IP, gateway, subnet);
```

---

## Benefits of Standalone Mode

✅ **No Raspberry Pi needed** - simpler setup  
✅ **Lower latency** - no network round-trip for inference  
✅ **Works offline** - just need WiFi for ESP32 stream  
✅ **Lower power** - no separate Pi server  
✅ **Portable** - just ESP32 + Android device  

---

## Troubleshooting

### ❌ "ESP32 not found" or Connection timeout

**Possible causes:**
1. ESP32 not connected to WiFi
2. Wrong IP address in app
3. ESP32 and Android on different networks

**Solutions:**
1. Check Serial Monitor for ESP32 IP
2. Ping ESP32 from Android: Use network tools app
3. Verify both devices on same WiFi network
4. Check ESP32 port 81 is accessible

### ❌ "AI model failed to load"

**Possible causes:**
1. TFLite model file missing from assets
2. Model file corrupted

**Solutions:**
1. Verify `my_model_float32.tflite` exists in `app/src/main/assets/`
2. Rebuild app
3. Check app permissions (storage/camera)

### ❌ No camera preview

**Possible causes:**
1. ESP32 not streaming
2. Network issues
3. Wrong port number

**Solutions:**
1. Check ESP32 Serial Monitor for errors
2. Test stream in browser: `http://ESP32_IP:81/stream`
3. Verify port 81 in app matches ESP32

### ❌ Slow detection/frame rate

**Possible causes:**
1. Android device underpowered
2. Network latency
3. Model too complex

**Solutions:**
1. Use faster Android device
2. Check WiFi signal strength
3. Reduce ESP32 frame rate
4. Optimize model or use lower resolution

---

## Testing

### Test ESP32 Camera Stream

**In browser on same WiFi:**
```
http://192.168.1.XXX:81/stream
```

Should see live camera feed.

### Test ESP32 Status Endpoint

```bash
curl http://192.168.1.XXX/status
```

### Test Android App Connection

1. Open app
2. Check logs in Android Studio Logcat:
   ```
   D/Dashboard: Connecting to MJPEG stream: http://192.168.1.XXX:81/stream
   D/Dashboard: Successfully connected to ESP32 MJPEG stream
   ```

---

## Comparison: Standalone vs Raspberry Pi

| Feature | Standalone (ESP32 + Android) | Raspberry Pi Setup |
|---------|------------------------------|-------------------|
| **Setup Complexity** | ⭐ Simple | ⭐⭐⭐ Complex |
| **Latency** | ⭐⭐⭐ Low (local inference) | ⭐⭐ Medium (network) |
| **Power Usage** | ⭐⭐⭐ Low | ⭐⭐ Medium |
| **Portability** | ⭐⭐⭐ High | ⭐ Low |
| **Processing Power** | ⭐⭐ Android device dependent | ⭐⭐⭐ Pi 5 is powerful |
| **Network Required** | ✅ Same WiFi | ✅ Same WiFi |
| **Raspberry Pi Needed** | ❌ No | ✅ Yes |

---

## Quick Start Checklist

- [ ] ESP32 code uploaded with correct WiFi credentials
- [ ] ESP32 powered on and connected to WiFi
- [ ] ESP32 IP address noted from Serial Monitor
- [ ] Android app configured: `USE_PI_DETECTION = false`
- [ ] ESP32 IP set in app code or device pairing
- [ ] Both ESP32 and Android on same WiFi network
- [ ] App rebuilt and installed
- [ ] Camera preview visible in app
- [ ] Drowsiness detection working

---

## Usage

1. **Power on ESP32** (connect USB or external power)
2. **Wait for WiFi connection** (LED indicator or Serial Monitor)
3. **Open Android app**
4. **Login** to Smart Helmet app
5. **Camera preview should appear** automatically
6. **Detection runs automatically** - no extra steps needed!

That's it! Just plug in ESP32, open app, and it works! 🎉

---

## Advanced: Auto-Connect Feature

The app can be configured to automatically connect to ESP32 on startup:

- The MJPEG stream connection happens automatically when Dashboard loads
- Device pairing settings are saved in SharedPreferences
- IP address can be discovered via network scan (future feature)

---

Need help? Check logs in Android Studio Logcat or ESP32 Serial Monitor!

