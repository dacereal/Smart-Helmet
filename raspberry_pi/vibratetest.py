import RPi.GPIO as GPIO
import time

motorPin = 18  # GPIO 18 (Pin 12 on the header)

GPIO.setmode(GPIO.BCM)
GPIO.setup(motorPin, GPIO.OUT)

print("Vibration Motor Test Starting...")

try:
    while True:
        print("Motor ON")
        GPIO.output(motorPin, GPIO.HIGH)
        time.sleep(1)

        print("Motor OFF")
        GPIO.output(motorPin, GPIO.LOW)
        time.sleep(1)
except KeyboardInterrupt:
    print("Exiting...")
finally:
    GPIO.cleanup()
