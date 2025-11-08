#!/bin/bash
# Alternative installation script that avoids SSH timeout issues
# Run this directly on the Pi to avoid connection issues

echo "Installing dependencies without opencv-python (using system package)..."

cd ~/smart_helmet_pi

# Activate virtual environment if it exists, otherwise create it
if [ ! -d "venv" ]; then
    echo "Creating virtual environment..."
    python3 -m venv venv
fi

source venv/bin/activate

# Install packages without opencv (system package is already installed)
echo "Installing onnxruntime..."
pip3 install --default-timeout=1000 onnxruntime

echo "Installing flask and flask-cors..."
pip3 install --default-timeout=1000 flask flask-cors

echo "Installing numpy..."
pip3 install --default-timeout=1000 numpy

echo ""
echo "Installation complete!"
echo "Test with: python3 -c 'import cv2; import onnxruntime; print(\"OK\")'"

