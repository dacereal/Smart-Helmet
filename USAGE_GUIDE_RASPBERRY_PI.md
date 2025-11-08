# How to Use Smart Helmet App with Raspberry Pi

## Prerequisites

1. **Raspberry Pi 5** must be powered on and connected to the same Wi-Fi network as your Android device
2. **Pi server** must be running
3. **Android device** and Pi must be on the same network

## Step-by-Step Usage Guide

### Step 1: Start the Raspberry Pi Server

**On Raspberry Pi (SSH session):**

```bash
cd ~/smart_helmet_pi
source venv/bin/activate
python3 drowsiness_server.py
```

**Expected output:**
```
INFO:drowsiness_detector:Model loaded successfully from /home/smarthelmet/smart_helmet_pi/models/my_model.onnx
INFO:__main__:Drowsiness detector initialized successfully
 * Running on http://192.168.43.151:5000
```

**⚠️ Important:** Keep this SSH session running while using the app!

---

### Step 2: Verify Pi Server is Reachable

**From Windows PowerShell:**
```powershell
curl http://192.168.43.151:5000/health
```

**Or from another Pi SSH session:**
```bash
curl http://192.168.43.151:5000/health
```

**Expected response:**
```json
{"detector_loaded":true,"model_path":"/home/smarthelmet/smart_helmet_pi/models/my_model.onnx","status":"ok"}
```

If you get a response, the server is working! ✅

---

### Step 3: Build and Run Android App

**Option A: Using Android Studio**
1. Open the project in Android Studio
2. Connect your Android device via USB (enable USB debugging)
3. Click **Run** or press `Shift+F10`
4. Select your device when prompted

**Option B: Using Gradle (Command Line)**
```powershell
# From project root directory
.\gradlew installDebug
```

**Option C: Generate APK**
```powershell
.\gradlew assembleDebug
# APK will be in: app\build\outputs\apk\debug\app-debug.apk
```

---

### Step 4: Launch and Use the App

1. **Open the Smart Helmet app** on your Android device

2. **Login** with your Firebase credentials

3. **Check for Connection Message:**
   - You should see a toast: **"Connected to Raspberry Pi"**
   - If you see "Warning: Pi server not reachable", check:
     - Pi server is running
     - Android device and Pi are on same Wi-Fi network
     - IP address is correct (`192.168.43.151`)

4. **Navigate to Dashboard:**
   - The app will show the camera preview
   - If drowsiness detection is active, you'll see detection boxes

5. **What You'll See:**
   - **Green box + "Alert" status** = Driver is awake
   - **Red box + "Drowsy" status** = Driver is drowsy
   - **Alarm triggered** = Drowsiness detected for 2+ seconds

---

### Step 5: Monitor Performance

**Check Pi Server Logs (SSH session):**
Look for requests like:
```
INFO:werkzeug:192.168.43.X - - [31/Oct/2025 21:XX:XX] "POST /detect_simple HTTP/1.1" 200 -
```

**Check Android Logcat (Android Studio):**
Filter by "Dashboard" or "PiDrowsinessDetector" to see:
```
D/Dashboard: Frame #X Pi ML result: AWAKE/DROWSY
D/PiDrowsinessDetector: Response code: 200
```

---

## Configuration

### Change Pi Server IP Address

If your Pi has a different IP address:

1. **Find Pi IP:**
   ```bash
   # On Pi
   hostname -I
   ```

2. **Update Android App:**
   
   Edit `app/src/main/java/com/botsquad/smarthelmet/Dashboard.java`:
   
   ```java
   private static final String PI_SERVER_URL = "http://YOUR_PI_IP:5000";
   ```
   
   Rebuild and install the app.

### Switch Between Pi and Local Detection

In `Dashboard.java`:
```java
// Use Raspberry Pi
private static final boolean USE_PI_DETECTION = true;

// OR use local Android inference
private static final boolean USE_PI_DETECTION = false;
```

---

## Troubleshooting

### ❌ "Pi server not reachable"

**Possible causes:**
1. Pi server not running → Start it with `python3 drowsiness_server.py`
2. Wrong IP address → Check Pi IP with `hostname -I` on Pi
3. Different Wi-Fi networks → Ensure Android device and Pi are on same network
4. Firewall blocking → Check Pi firewall settings

**Solution:**
```bash
# On Pi, check if port 5000 is open
sudo ufw allow 5000
sudo netstat -tlnp | grep 5000
```

### ❌ "Connection timed out"

**Possible causes:**
1. Network congestion
2. Large image transfers taking too long

**Solution:**
- Ensure good Wi-Fi signal strength
- Reduce image quality in `PiDrowsinessDetector.java` (line ~60): Change JPEG quality from 80 to 60

### ❌ Slow Detection Response

**Possible causes:**
1. Network latency
2. Pi CPU overload

**Solution:**
- Check Pi CPU usage: `htop` on Pi
- Optimize by reducing image size before sending
- Consider running Pi server with more threads

### ❌ App Crashes

**Check logs:**
```bash
# Android Logcat
adb logcat | grep -E "(Dashboard|PiDrowsinessDetector|FATAL)"

# Pi server logs
# Check the SSH session where server is running
```

---

## Auto-Start Pi Server (Optional)

To start Pi server automatically on boot:

```bash
# On Pi
cd ~/smart_helmet_pi
sudo cp drowsiness.service /etc/systemd/system/
sudo systemctl enable drowsiness.service
sudo systemctl start drowsiness.service

# Check status
sudo systemctl status drowsiness.service
```

**Now the server will start automatically on Pi boot!** 🎉

---

## Testing the Integration

### Quick Test

1. **Start Pi server** (Step 1)
2. **Run Android app** (Step 3)
3. **Look at camera preview** - You should see:
   - Camera feed displayed
   - Detection boxes if faces are detected
   - Status updates (Alert/Drowsy)

### Manual Test with curl

Test the Pi server directly:

```powershell
# From Windows (requires base64 encoded image)
# This tests the /detect_simple endpoint
curl -X POST http://192.168.43.151:5000/detect_simple `
  -H "Content-Type: application/json" `
  -d '{"image":"BASE64_IMAGE_STRING"}'
```

---

## Performance Tips

1. **Reduce Image Quality** (faster transmission):
   - Edit `PiDrowsinessDetector.java`, line ~60
   - Change: `bitmap.compress(Bitmap.CompressFormat.JPEG, 80, ...)`
   - Lower number = smaller file = faster upload

2. **Process Every Nth Frame** (reduce load):
   - Edit `Dashboard.java` to skip frames
   - Only process every 2nd or 3rd frame

3. **Optimize Network**:
   - Use 5GHz Wi-Fi if available
   - Keep Android device close to router
   - Minimize other network usage

---

## What's Happening Behind the Scenes

1. **Android app** captures camera frame
2. **Converts to JPEG** and base64 encodes
3. **Sends HTTP POST** to `http://192.168.43.151:5000/detect_simple`
4. **Pi server** receives image, runs ONNX model inference
5. **Pi returns** `{"is_drowsy": true/false, "confidence": 0.85}`
6. **Android app** updates UI and triggers alarms if drowsy

---

## Success Indicators ✅

- ✅ Pi server shows incoming requests
- ✅ Android app shows "Connected to Raspberry Pi" toast
- ✅ Camera preview displays with detection boxes
- ✅ Status updates (Alert/Drowsy) work correctly
- ✅ Alarms trigger when drowsy

---

Need help? Check the logs on both Pi and Android device!

