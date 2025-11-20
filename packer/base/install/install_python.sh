#!/bin/bash
set -e

echo "=== Running: install_python.sh ==="

# Ensure non-interactive mode for apt
export DEBIAN_FRONTEND=noninteractive

# Add deadsnakes PPA for additional Python versions
sudo DEBIAN_FRONTEND=noninteractive add-apt-repository ppa:deadsnakes/ppa -y
sudo DEBIAN_FRONTEND=noninteractive apt update -y

# Install Python 3.10 with development packages
sudo DEBIAN_FRONTEND=noninteractive apt install -y python3.10 python3.10-dev python3.10-venv python3-pip

# Setup update-alternatives for python version management
# Make Python 3.10 the default python command
sudo update-alternatives --install /usr/bin/python python /usr/bin/python3.10 1
sudo update-alternatives --set python /usr/bin/python3.10

# Install uv (fast Python package installer) via direct binary download
echo "Installing uv to /usr/local/bin..."

# Detect architecture
ARCH=$(uname -m)
case $ARCH in
    x86_64)  UV_ARCH="x86_64-unknown-linux-gnu" ;;
    aarch64) UV_ARCH="aarch64-unknown-linux-gnu" ;;
    *) echo "Unsupported architecture: $ARCH"; exit 1 ;;
esac

# Download latest release from GitHub
UV_VERSION=$(curl -s https://api.github.com/repos/astral-sh/uv/releases/latest | grep '"tag_name"' | cut -d'"' -f4)
echo "Downloading uv ${UV_VERSION} for ${UV_ARCH}..."
curl -LsSf "https://github.com/astral-sh/uv/releases/download/${UV_VERSION}/uv-${UV_ARCH}.tar.gz" -o /tmp/uv.tar.gz
sudo tar xzf /tmp/uv.tar.gz -C /usr/local/bin --strip-components=1
rm /tmp/uv.tar.gz

# Verify installation
uv --version

# Install iostat-tool using uv
uv tool install iostat-tool

# Create symlink for iostat-cli
# Find the installed location and create symlink
sudo ln -sf $(which iostat-cli) /usr/local/bin/iostat-cli

echo "✓ install_python.sh completed successfully"
