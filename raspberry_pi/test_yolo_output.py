#!/usr/bin/env python3
"""
Test script to check YOLO ONNX model output format
"""

import cv2
import numpy as np
import onnxruntime as ort
import os

# Load model
MODEL_PATH = "my_model.onnx"
if not os.path.exists(MODEL_PATH):
    MODEL_PATH = os.path.join("..", "app", "src", "main", "assets", "my_model.onnx")

if not os.path.exists(MODEL_PATH):
    print(f"Model not found at {MODEL_PATH}")
    exit(1)

# Initialize ONNX session
session = ort.InferenceSession(MODEL_PATH)
input_name = session.get_inputs()[0].name
output_names = [output.name for output in session.get_outputs()]

print(f"Input name: {input_name}")
print(f"Input shape: {session.get_inputs()[0].shape}")
print(f"Output names: {output_names}")
for i, output in enumerate(session.get_outputs()):
    print(f"Output {i} shape: {output.shape}")

# Create a test image
test_image = np.zeros((640, 640, 3), dtype=np.uint8)
test_image = cv2.resize(test_image, (640, 640))

# Preprocess
rgb = cv2.cvtColor(test_image, cv2.COLOR_BGR2RGB)
normalized = rgb.astype(np.float32) / 255.0
transposed = np.transpose(normalized, (2, 0, 1))
batched = np.expand_dims(transposed, axis=0)

# Run inference
outputs = session.run(output_names, {input_name: batched})

print(f"\nNumber of outputs: {len(outputs)}")
for i, output in enumerate(outputs):
    print(f"\nOutput {i}:")
    print(f"  Shape: {output.shape}")
    print(f"  Dtype: {output.dtype}")
    print(f"  Min: {output.min()}, Max: {output.max()}")
    if len(output.shape) == 3:
        print(f"  Sample values (first detection): {output[0, :, 0]}")
    elif len(output.shape) == 2:
        print(f"  Sample values (first detection): {output[0, :]}")
