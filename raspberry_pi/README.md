# Raspberry Pi Drowsiness Detection Service

This service runs on Raspberry Pi 5 and handles machine learning inference for drowsiness detection, offloading the work from the Android app.

## Features

- **ONNX Runtime** - Optimized for ARM64 (Raspberry Pi 5)
- **REST API** - Flask-based server for receiving camera frames
- **Fast Inference** - Multi-threaded ONNX Runtime using all Pi 5 cores
- **Low Latency** - Optimized preprocessing and postprocessing

## Prerequisites

- Raspberry Pi 5 running Debian/Raspberry Pi OS
- Python 3.8 or higher
- ONNX model file (`my_model.onnx`)

## Setup

1. **Copy files to Raspberry Pi:**

   From your Windows machine:
   ```powershell
   scp -r raspberry_pi/* smarthelmet@pi.local:/home/smarthelmet/smart_helmet_pi/
   ```

2. **SSH into Raspberry Pi:**

   ```bash
   ssh smarthelmet@pi.local
   ```

3. **Run setup script:**

   ```bash
   cd ~/smart_helmet_pi
   chmod +x setup.sh
   ./setup.sh
   ```

4. **Copy model file:**

   From Windows:
   ```powershell
   scp app\src\main\assets\my_model.onnx smarthelmet@pi.local:/home/smarthelmet/smart_helmet_pi/models/
   ```

   Or manually copy via SFTP/SMB.

## Running the Service

### Manual Start

```bash
cd ~/smart_helmet_pi
source venv/bin/activate
python3 drowsiness_server.py
```

The server will start on `http://0.0.0.0:5000`

### As a System Service (Recommended)

1. Copy service file:
   ```bash
   sudo cp drowsiness.service /etc/systemd/system/
   ```

2. Enable and start:
   ```bash
   sudo systemctl enable drowsiness.service
   sudo systemctl start drowsiness.service
   ```

3. Check status:
   ```bash
   sudo systemctl status drowsiness.service
   ```

4. View logs:
   ```bash
   journalctl -u drowsiness.service -f
   ```

## API Endpoints

### Health Check
```
GET http://pi.local:5000/health
```

Response:
```json
{
  "status": "ok",
  "detector_loaded": true,
  "model_path": "/path/to/model"
}
```

### Detect Drowsiness (Full)
```
POST http://pi.local:5000/detect
Content-Type: application/json

{
  "image": "base64_encoded_image_string"
}
```

Response:
```json
{
  "is_drowsy": true,
  "confidence": 0.85,
  "detections": [
    {
      "bbox": [100, 150, 200, 250],
      "confidence": 0.85,
      "class_id": 1,
      "label": "Drowsy"
    }
  ]
}
```

### Detect Drowsiness (Simple)
```
POST http://pi.local:5000/detect_simple
Content-Type: application/json

{
  "image": "base64_encoded_image_string"
}
```

Response:
```json
{
  "is_drowsy": true,
  "confidence": 0.85
}
```

## Performance

- **Inference Time**: ~50-100ms per frame (on Pi 5)
- **Throughput**: ~10-20 FPS
- **Memory Usage**: ~200-300MB
- **CPU Usage**: ~30-50% (all 4 cores)

## Troubleshooting

### Model not found
- Ensure `my_model.onnx` is in `models/` directory
- Check file permissions: `chmod 644 models/my_model.onnx`

### Port already in use
- Change port in `drowsiness_server.py`: `app.run(port=5001)`
- Or kill existing process: `sudo lsof -ti:5000 | xargs sudo kill`

### Import errors
- Activate virtual environment: `source venv/bin/activate`
- Reinstall dependencies: `pip3 install -r requirements.txt`

### Slow performance
- Ensure using Python 3.8+
- Check ONNX Runtime version: `python3 -c "import onnxruntime; print(onnxruntime.__version__)"`
- Monitor CPU usage: `htop`

## Network Configuration

The service listens on `0.0.0.0:5000` to accept connections from other devices on the network. Ensure:

1. **Firewall allows port 5000:**
   ```bash
   sudo ufw allow 5000
   ```

2. **Android app can reach Pi:**
   - Test connection: `curl http://pi.local:5000/health`
   - Or use IP: `curl http://192.168.43.151:5000/health`

## Next Steps

After setting up the Pi service, modify the Android app to:
1. Send camera frames to Raspberry Pi instead of local inference
2. Receive detection results from Pi
3. Handle network errors gracefully

See the Android integration guide for code changes.

