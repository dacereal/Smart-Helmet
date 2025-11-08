# Troubleshooting: Pi Stops/Disconnects After Starting Server

## Symptoms
- Pi stops responding after running `drowsiness_server_pi_camera.py`
- Network connection drops
- Pi becomes unresponsive

## Common Causes & Solutions

### 1. **Power Supply Issues** (Most Common)

**Problem:** Insufficient power causing Pi to brown out and shutdown

**Solutions:**
- Use official Raspberry Pi 5 power supply (27W minimum)
- Check power adapter is properly connected
- Try different power cable
- Check for loose power connections

**Check:**
```bash
# Check power status (run this BEFORE server stops)
vcgencmd get_throttled

# Should return: throttled=0x0 (no throttling)
# If you see 0x50000 or similar, power issue!
```

---

### 2. **Overheating**

**Problem:** Pi 5 overheating and thermal shutdown

**Solutions:**
- Ensure adequate cooling (heatsink/fan)
- Check temperature: `vcgencmd measure_temp`
- Reduce CPU load
- Use safe mode server (lower frame rate)

**Check temperature:**
```bash
vcgencmd measure_temp
# Should be below 70°C ideally
```

---

### 3. **Memory Exhaustion**

**Problem:** Server using too much RAM

**Solutions:**
- Use safe mode server (reduced processing)
- Close other applications
- Increase swap space if needed

**Check memory:**
```bash
free -h
```

---

### 4. **CPU Overload**

**Problem:** Continuous camera capture + inference too intensive

**Solutions:**
- Use safe mode version: `drowsiness_server_pi_camera_safe.py`
- Reduce frame processing rate
- Process every Nth frame instead of every frame

---

## Quick Fix: Use Safe Mode Server

**Use the safer version with reduced load:**

```bash
cd ~/smart_helmet_pi
source venv/bin/activate
python3 drowsiness_server_pi_camera_safe.py
```

**Safe mode features:**
- Lower frame rate (2 FPS vs 10 FPS)
- Processes every 3rd frame instead of every frame
- Garbage collection to manage memory
- Longer sleep intervals on errors

---

## Monitor Pi Health

**Before starting server, check:**
```bash
# Check power/throttling
vcgencmd get_throttled

# Check temperature
vcgencmd measure_temp

# Check memory
free -h

# Check CPU usage
top
```

**Run in another terminal while server is running:**
```bash
# Monitor temperature
watch -n 1 vcgencmd measure_temp

# Monitor throttling
watch -n 1 vcgencmd get_throttled
```

---

## Recommendations

1. **Use safe mode server** (`drowsiness_server_pi_camera_safe.py`)
2. **Check power supply** - Use official Pi 5 power adapter
3. **Add cooling** - Heatsink or fan if temperature is high
4. **Monitor health** - Check `vcgencmd get_throttled` before crashes

---

## If Pi Still Crashes

**Try these in order:**
1. Use safe mode server (lower load)
2. Check power supply (most common issue!)
3. Check temperature (add cooling)
4. Reduce camera resolution in code
5. Process fewer frames (increase sleep time)

---

## Testing Without Full Load

**Test server without camera first:**
```bash
# Comment out camera initialization temporarily
# Just test Flask server without camera
# If server stays up without camera, issue is camera/processing load
```

---

Most likely issue is **power supply** - make sure you're using official Pi 5 power adapter!

