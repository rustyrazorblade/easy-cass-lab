#!/bin/bash
set -e

echo "=== Running: install_awscli.sh ==="

cd /tmp

cpu_arch=$(uname -m)
if [[ "$cpu_arch" == "x86_64" ]]; then
    curl -sL "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "awscliv2.zip"
elif [[ "$cpu_arch" == "aarch64" ]]; then
    curl -sL "https://awscli.amazonaws.com/awscli-exe-linux-aarch64.zip" -o "awscliv2.zip"
else
    echo "Unsupported architecture: $cpu_arch"
    exit 1
fi

unzip -q awscliv2.zip
sudo ./aws/install
rm -rf aws awscliv2.zip

# Verify installation
/usr/local/bin/aws --version

echo "✓ install_awscli.sh completed successfully"
