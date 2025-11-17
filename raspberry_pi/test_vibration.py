#!/usr/bin/env python3
"""
Test script for vibration motor
Run this to test if the vibration motor works independently
"""

import time
import logging
import argparse
import sys

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# GPIO for vibration motor control
try:
    from gpiozero import OutputDevice
    GPIO_AVAILABLE = True
    GPIO_LEGACY = False
except ImportError:
    try:
        import RPi.GPIO as GPIO
        GPIO_AVAILABLE = True
        GPIO_LEGACY = True
    except ImportError:
        GPIO_AVAILABLE = False
        logger.error("GPIO libraries not available. Install with: pip install gpiozero")

# Vibration motor GPIO pin default (BCM numbering unless --board is used)
VIBRATION_GPIO_PIN = 18

def test_vibration(
    pin: int,
    use_board_numbering: bool,
    active_high: bool,
    on_seconds: float,
    off_seconds: float,
    count: int,
    use_pwm: bool,
    pwm_frequency_hz: float,
    pwm_duty_percent: float,
):
    """Test the vibration motor with configurable parameters."""
    if not GPIO_AVAILABLE:
        logger.error("GPIO not available. Cannot test vibration motor.")
        return False
    
    try:
        numbering = "BOARD" if use_board_numbering else "BCM"
        logger.info(
            f"Testing vibration motor on pin {pin} ({numbering} numbering), "
            f"active_high={active_high}, on={on_seconds}s, off={off_seconds}s, "
            f"count={'infinite' if count <= 0 else count}, pwm={use_pwm}"
        )
        
        if GPIO_LEGACY:
            # Use RPi.GPIO
            GPIO.setmode(GPIO.BOARD if use_board_numbering else GPIO.BCM)
            GPIO.setup(pin, GPIO.OUT, initial=GPIO.LOW if active_high else GPIO.HIGH)
            pwm = None
            if use_pwm:
                pwm = GPIO.PWM(pin, pwm_frequency_hz)
                pwm.start(0 if active_high else 100)  # start OFF
            logger.info("GPIO initialized (RPi.GPIO)")
        else:
            # Use gpiozero
            if use_pwm:
                try:
                    from gpiozero import PWMOutputDevice
                except ImportError:
                    logger.error("gpiozero PWMOutputDevice not available. Install/update gpiozero.")
                    return False
                vibration_motor = PWMOutputDevice(pin, active_high=active_high, initial_value=0.0, frequency=pwm_frequency_hz)
            else:
                vibration_motor = OutputDevice(pin, active_high=active_high, initial_value=False)
            logger.info("GPIO initialized (gpiozero)")

        # Helper: set motor state
        def motor_on():
            if GPIO_LEGACY:
                if use_pwm:
                    # Set duty to requested value
                    duty = pwm_duty_percent if active_high else 100 - pwm_duty_percent
                    pwm.ChangeDutyCycle(max(0.0, min(100.0, duty)))
                else:
                    GPIO.output(pin, GPIO.HIGH if active_high else GPIO.LOW)
            else:
                if use_pwm:
                    vibration_motor.value = max(0.0, min(1.0, pwm_duty_percent / 100.0))
                else:
                    vibration_motor.on()

        def motor_off():
            if GPIO_LEGACY:
                if use_pwm:
                    duty = 0.0 if active_high else 100.0
                    pwm.ChangeDutyCycle(duty)
                else:
                    GPIO.output(pin, GPIO.LOW if active_high else GPIO.HIGH)
            else:
                if use_pwm:
                    vibration_motor.value = 0.0
                else:
                    vibration_motor.off()

        # Blink like Arduino example: ON then OFF with delays
        iterations = 0
        logger.info("Starting vibration test loop. Press Ctrl+C to stop.")
        while True:
            if count > 0 and iterations >= count:
                break
            logger.info("Motor ON")
            motor_on()
            time.sleep(on_seconds)
            logger.info("Motor OFF")
            motor_off()
            time.sleep(off_seconds)
            iterations += 1
        
        logger.info("✅ Vibration test loop completed!")
        
        # Cleanup
        if GPIO_LEGACY:
            try:
                if use_pwm and 'pwm' in locals() and pwm is not None:
                    pwm.stop()
            finally:
                GPIO.cleanup()
        else:
            vibration_motor.close()
        
        return True
        
    except Exception as e:
        logger.error(f"❌ Error testing vibration motor: {e}", exc_info=True)
        return False

def parse_args(argv):
    parser = argparse.ArgumentParser(description="Vibration Motor Test (Raspberry Pi)")
    parser.add_argument("--pin", type=int, default=VIBRATION_GPIO_PIN, help="GPIO pin number (BCM by default unless --board is set)")
    parser.add_argument("--board", action="store_true", help="Use BOARD numbering instead of BCM")
    parser.add_argument("--active-low", action="store_true", help="Set if your driver is active-low (inverts logic)")
    parser.add_argument("--on", type=float, default=1.0, help="ON duration in seconds (default: 1.0)")
    parser.add_argument("--off", type=float, default=1.0, help="OFF duration in seconds (default: 1.0)")
    parser.add_argument("--count", type=int, default=5, help="Number of ON/OFF cycles; <=0 means run forever (default: 5)")
    parser.add_argument("--pwm", action="store_true", help="Use PWM for control instead of simple on/off")
    parser.add_argument("--freq", type=float, default=100.0, help="PWM frequency in Hz (default: 100)")
    parser.add_argument("--duty", type=float, default=100.0, help="PWM duty cycle percent for ON state (0-100, default: 100)")
    return parser.parse_args(argv)


if __name__ == "__main__":
    args = parse_args(sys.argv[1:])
    print("=" * 60)
    print("Vibration Motor Test")
    print("=" * 60)
    numbering = "BOARD" if args.board else "BCM"
    print(f"Pin: {args.pin} ({numbering}) | active_high={not args.active_low} | on={args.on}s | off={args.off}s | count={args.count}")
    if args.pwm:
        print(f"PWM: freq={args.freq} Hz, duty_on={args.duty}%")
    print("Make sure your wiring uses a transistor/MOSFET and flyback diode, and grounds are common.")
    print("=" * 60)
    print()
    
    VIBRATION_GPIO_PIN = args.pin  # keep global consistent for logs if referenced
    test_vibration(
        pin=args.pin,
        use_board_numbering=args.board,
        active_high=not args.active_low,
        on_seconds=args.on,
        off_seconds=args.off,
        count=args.count,
        use_pwm=args.pwm,
        pwm_frequency_hz=args.freq,
        pwm_duty_percent=args.duty,
    )

