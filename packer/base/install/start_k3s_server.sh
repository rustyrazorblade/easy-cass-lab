#!/bin/bash

set -e

echo "=== Starting K3s Server ==="

# Get registry IP (optional first argument, defaults to auto-detected private IP)
REGISTRY_IP="${1:-}"
REGISTRY_PORT="${2:-5000}"

if [ -z "$REGISTRY_IP" ]; then
    # Auto-detect private IP (first non-localhost IP)
    REGISTRY_IP=$(hostname -I | awk '{print $1}')
    echo "Auto-detected registry IP: $REGISTRY_IP"
fi

# Configure insecure registry for K3s
echo "Configuring insecure registry at ${REGISTRY_IP}:${REGISTRY_PORT}..."
sudo mkdir -p /etc/rancher/k3s
sudo tee /etc/rancher/k3s/registries.yaml > /dev/null <<EOF
mirrors:
  "${REGISTRY_IP}:${REGISTRY_PORT}":
    endpoint:
      - "http://${REGISTRY_IP}:${REGISTRY_PORT}"
EOF
echo "✓ Registry configuration written to /etc/rancher/k3s/registries.yaml"

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
