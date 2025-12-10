#!/bin/bash
# Configure containerd to trust the registry's TLS certificate
# Arguments: REGISTRY_HOST REGISTRY_PORT S3_BUCKET S3_PATH

set -e

REGISTRY_HOST=$1
REGISTRY_PORT=$2
S3_BUCKET=$3
S3_PATH=$4

CERT_DIR="/etc/containerd/certs.d/${REGISTRY_HOST}:${REGISTRY_PORT}"

sudo mkdir -p "$CERT_DIR"

# Download certificate from S3 (use full path since sudo doesn't preserve PATH)
sudo /usr/local/bin/aws s3 cp "s3://$S3_BUCKET/$S3_PATH" "$CERT_DIR/ca.crt"

# Create hosts.toml configuration for containerd
sudo tee "$CERT_DIR/hosts.toml" > /dev/null <<EOF
server = "https://${REGISTRY_HOST}:${REGISTRY_PORT}"

[host."https://${REGISTRY_HOST}:${REGISTRY_PORT}"]
  ca = "$CERT_DIR/ca.crt"
EOF

# Restart containerd to apply changes
sudo systemctl restart containerd
