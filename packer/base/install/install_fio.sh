#!/usr/bin/env bash
set -euo pipefail

VERSION="3.37"
WORK_DIR=""

# Cleanup function for trap
cleanup() {
    if [ -n "$WORK_DIR" ] && [ -d "$WORK_DIR" ]; then
        echo "Cleaning up temporary directory..."
        rm -rf "$WORK_DIR"
    fi
}

trap cleanup EXIT

# Check if fio is already installed
if command -v fio &> /dev/null; then
    INSTALLED_VERSION=$(fio --version 2>&1 | head -n1 | awk '{print $2}' || echo "unknown")
    echo "fio already installed (version: $INSTALLED_VERSION), skipping installation"
    exit 0
fi

echo "Installing fio version ${VERSION}..."

# Create temp directory for build
WORK_DIR=$(mktemp -d)
cd "$WORK_DIR"

# Download fio source
echo "Downloading fio ${VERSION}..."
wget -q --show-progress "https://github.com/axboe/fio/archive/refs/tags/fio-${VERSION}.zip"

# Verify download succeeded and has content
if [ ! -s "fio-${VERSION}.zip" ]; then
    echo "ERROR: Download failed or file is empty"
    exit 1
fi

echo "Extracting source..."
unzip -q "fio-${VERSION}.zip"

# Build and install fio
echo "Building fio (this may take a few minutes)..."
cd fio-fio-${VERSION}

echo "Running configure..."
./configure > /dev/null

echo "Compiling..."
make -j$(nproc)

echo "Installing fio..."
sudo make install > /dev/null

# Verify installation
echo "Verifying fio installation..."
if ! command -v fio &> /dev/null; then
    echo "ERROR: fio command not found after installation"
    exit 1
fi

INSTALLED_VERSION=$(fio --version 2>&1 | head -n1)
echo "fio installed successfully: $INSTALLED_VERSION"
