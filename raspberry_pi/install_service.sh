#!/bin/bash
# Helper script to install and enable the Smart Helmet drowsiness service.

set -euo pipefail

SERVICE_NAME="drowsiness.service"
SYSTEMD_PATH="/etc/systemd/system/${SERVICE_NAME}"

echo "========================================="
echo "Installing Smart Helmet drowsiness service"
echo "========================================="

if [[ $EUID -ne 0 ]]; then
    echo "This script must be run as root (use: sudo ./install_service.sh)"
    exit 1
fi

if [[ ! -f "${SERVICE_NAME}" ]]; then
    echo "Cannot find ${SERVICE_NAME} in the current directory."
    echo "Please run this script from raspberry_pi/."
    exit 1
fi

echo "Copying ${SERVICE_NAME} to ${SYSTEMD_PATH}..."
install -m 644 "${SERVICE_NAME}" "${SYSTEMD_PATH}"

echo "Reloading systemd daemon..."
systemctl daemon-reload

echo "Enabling service to start on boot..."
systemctl enable "${SERVICE_NAME}"

echo "Starting service now..."
systemctl restart "${SERVICE_NAME}"

echo ""
echo "Service status:"
systemctl status "${SERVICE_NAME}" --no-pager

echo ""
echo "Installation complete."
echo "Use 'journalctl -u ${SERVICE_NAME} -f' to follow logs."
echo "========================================="
