#!/bin/bash

set -e

echo "=== Starting K3s Agent ==="

# Check arguments
if [ $# -lt 2 ]; then
    echo "Usage: $0 <server_url> <node_token> [registry_ip] [registry_port]"
    echo "Example: $0 https://10.0.1.5:6443 K10abc... 10.0.1.5 5000"
    exit 1
fi

SERVER_URL="$1"
NODE_TOKEN="$2"
REGISTRY_IP="${3:-}"
REGISTRY_PORT="${4:-5000}"

echo "Server URL: $SERVER_URL"

# If registry IP not provided, extract from server URL
if [ -z "$REGISTRY_IP" ]; then
    # Extract IP from server URL (e.g., https://10.0.1.5:6443 -> 10.0.1.5)
    REGISTRY_IP=$(echo "$SERVER_URL" | sed -E 's|https?://([^:]+):.*|\1|')
    echo "Extracted registry IP from server URL: $REGISTRY_IP"
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

# Check if k3s-agent.service already exists
if systemctl list-unit-files k3s-agent.service --no-pager --no-legend 2>/dev/null | grep -q k3s-agent.service; then
    echo "k3s-agent.service already installed, skipping installation"
else
    echo "Installing k3s in agent mode..."

    # Run airgap installation in agent mode
    INSTALL_K3S_SKIP_DOWNLOAD=true \
    K3S_URL="$SERVER_URL" \
    K3S_TOKEN="$NODE_TOKEN" \
    /usr/local/bin/install-k3s.sh

    echo "✓ K3s agent installed successfully"
fi

# Start the k3s-agent service
echo "Starting k3s-agent.service..."
systemctl start k3s-agent

echo "✓ K3s agent started successfully"
