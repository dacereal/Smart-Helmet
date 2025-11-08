#!/bin/bash
# Setup script for Raspberry Pi drowsiness detection service

echo "========================================="
echo "Raspberry Pi Drowsiness Detection Setup"
echo "========================================="

# Update system packages
echo "Updating system packages..."
sudo apt-get update
sudo apt-get upgrade -y

# Install Python3 and pip if not already installed
echo "Installing Python dependencies..."
sudo apt-get install -y python3 python3-pip python3-venv

# Install system dependencies for OpenCV
echo "Installing OpenCV dependencies..."
sudo apt-get install -y libopencv-dev python3-opencv

# Create virtual environment (optional but recommended)
echo "Creating virtual environment..."
python3 -m venv venv
source venv/bin/activate

# Install Python packages
echo "Installing Python packages..."
pip3 install --upgrade pip
pip3 install -r requirements.txt

# Create models directory
echo "Creating models directory..."
mkdir -p models

# Check if model file exists
if [ ! -f "models/my_model.onnx" ]; then
    echo "WARNING: my_model.onnx not found in models/ directory"
    echo "Please copy your ONNX model file to raspberry_pi/models/"
    echo "You can copy it from: app/src/main/assets/my_model.onnx"
fi

# Make scripts executable
chmod +x drowsiness_detector.py
chmod +x drowsiness_server.py

echo ""
echo "========================================="
echo "Setup complete!"
echo "========================================="
echo ""
echo "To start the server:"
echo "  source venv/bin/activate  # If using venv"
echo "  python3 drowsiness_server.py"
echo ""
echo "Or run as a service:"
echo "  sudo cp drowsiness.service /etc/systemd/system/"
echo "  sudo systemctl enable drowsiness.service"
echo "  sudo systemctl start drowsiness.service"
echo ""

