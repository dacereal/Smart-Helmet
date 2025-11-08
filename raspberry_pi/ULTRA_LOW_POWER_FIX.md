# Ultra-Low Power Setup for Pi 5 (Without Official Power Supply)

## Emergency Power Saving Steps

### Step 1: Reduce CPU Frequency

**On Pi:**

```bash
# Edit config to reduce CPU frequency
sudo nano /boot/firmware/config.txt

# Add these lines at the end:
arm_freq=1200  # Reduce from default 2400MHz
over_voltage=-2  # Reduce voltage slightly

# Save and reboot
sudo reboot
```

---

### Step 2: Disable Unnecessary Services

```bash
# Disable services you don't need
sudo systemctl disable bluetooth
sudo systemctl stop bluetooth
sudo systemctl disable avahi-daemon
sudo systemctl stop avahi-daemon
sudo systemctl disable cups
sudo systemctl stop cups
sudo systemctl disable ModemManager
sudo systemctl stop ModemManager

# Check running services
sudo systemctl list-units --type=service --state=running | grep -v systemd
```

---

### Step 3: Reduce GPU/Video Usage

```bash
# In /boot/firmware/config.txt
gpu_mem=16  # Minimal GPU memory (default is 76MB)
```

---

### Step 4: Use Ultra-Low Power Server

Use the safe version that processes minimal frames:

```bash
cd ~/smart_helmet_pi
source venv/bin/activate
python3 drowsiness_server_pi_camera_safe.py
```

---

### Step 5: Monitor Power Status

```bash
# Check throttling in real-time
watch -n 1 vcgencmd get_throttled

# If it stays at 0x0 or only shows historical (not current), you're OK
```

---

## Alternative: Network Power Over USB-C

If you have a laptop with USB-C, try:
- Connect Pi to laptop's USB-C port
- Laptop can often provide more stable power than chargers

---

## Last Resort: Disable Camera Temporarily

If nothing works, you can:
1. Run Pi without camera (just test server)
2. Use Android app with local detection
3. Note in presentation: "Requires official Pi 5 power supply"

