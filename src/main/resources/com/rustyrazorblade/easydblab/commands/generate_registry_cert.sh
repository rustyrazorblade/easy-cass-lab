#!/bin/bash
# Generate self-signed TLS certificate for container registry
# Arguments: REGISTRY_IP CERT_DIR S3_BUCKET S3_PATH

set -e

REGISTRY_IP=$1
CERT_DIR=$2
S3_BUCKET=$3
S3_PATH=$4

sudo mkdir -p "$CERT_DIR"

sudo openssl req -newkey rsa:4096 -nodes -sha256 \
    -keyout "$CERT_DIR/registry.key" \
    -x509 -days 365 \
    -out "$CERT_DIR/registry.crt" \
    -subj "/CN=$REGISTRY_IP" \
    -addext "subjectAltName=IP:$REGISTRY_IP"

sudo chmod 600 "$CERT_DIR/registry.key"
sudo chmod 644 "$CERT_DIR/registry.crt"

# Upload certificate to S3
sudo aws s3 cp "$CERT_DIR/registry.crt" "s3://$S3_BUCKET/$S3_PATH"
