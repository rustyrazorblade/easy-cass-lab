#!/bin/bash

set -e

echo "=== Starting K3s Server ==="

# Check if k3s.service already exists
if systemctl list-unit-files k3s.service --no-pager --no-legend 2>/dev/null | grep -q k3s.service; then
    echo "k3s.service already installed, skipping installation"
else
    echo "Installing k3s in server mode..."

    # Run airgap installation in server mode
    INSTALL_K3S_SKIP_DOWNLOAD=true \
    INSTALL_K3S_EXEC='server --write-kubeconfig-mode=644' \
    /usr/local/bin/install-k3s.sh

    echo "✓ K3s server installed successfully"
fi

# Start the k3s service
echo "Starting k3s.service..."
systemctl start k3s

echo "✓ K3s server started successfully"
