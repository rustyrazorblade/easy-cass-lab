#!/bin/bash

# OpenTelemetry Collector version
OTEL_VERSION="0.123.0"

# Get the architecture using uname
cpu_arch=$(uname -m)

# Set ARCH based on the CPU architecture
if [[ "$cpu_arch" == "x86_64" ]]; then
    ARCH="amd64"
elif [[ "$cpu_arch" == "aarch64" ]]; then
    ARCH="arm64"
else
    echo "Unsupported architecture: $cpu_arch"
    exit 1
fi

echo "Installing OpenTelemetry Collector v${OTEL_VERSION} for ${ARCH} architecture"

# Install OpenTelemetry Collector
sudo apt-get update
sudo apt-get -y install wget
wget https://github.com/open-telemetry/opentelemetry-collector-releases/releases/download/v${OTEL_VERSION}/otelcol_${OTEL_VERSION}_linux_${ARCH}.deb
sudo dpkg -i otelcol_${OTEL_VERSION}_linux_${ARCH}.deb