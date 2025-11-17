# Commands to Copy Files to Raspberry Pi

## Copy Updated Files to Pi

From your Windows machine, run these commands in PowerShell:

```powershell
# Navigate to project directory
cd C:\Users\calvin\CAPSTONE\Smart_Helmet

# Copy the updated server file
scp raspberry_pi\drowsiness_server_pi_camera.py smarthelmet@pi.local:/home/smarthelmet/smart_helmet_pi/

# Copy the test script
scp raspberry_pi\test_vibration.py smarthelmet@pi.local:/home/smarthelmet/smart_helmet_pi/

# Copy requirements (if updated)
scp raspberry_pi\requirements.txt smarthelmet@pi.local:/home/smarthelmet/smart_helmet_pi/
```

## Or Copy All Python Files at Once

```powershell
scp raspberry_pi\*.py smarthelmet@pi.local:/home/smarthelmet/smart_helmet_pi/
```

## After Copying

1. SSH into your Pi:
   ```bash
   ssh smarthelmet@pi.local
   ```

2. Install gpiozero (if not installed):
   ```bash
   cd ~/smart_helmet_pi
   source venv/bin/activate
   pip install gpiozero
   ```

3. Test the vibration motor first:
   ```bash
   python3 test_vibration.py
   ```
   This will test if the motor works independently.

4. If test works, restart the server:
   ```bash
   python3 drowsiness_server_pi_camera.py
   ```

## Troubleshooting

- Check the logs for vibration messages
- Verify GPIO pin number matches your wiring
- Make sure gpiozero is installed
- Test with the test_vibration.py script first

