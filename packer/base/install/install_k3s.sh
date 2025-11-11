#!/bin/bash

set -x

# Install k3s on Ubuntu 22.04
# k3s will be installed but NOT enabled or started automatically
# Services can be started manually when needed

# Download and run k3s installer
# Using the latest stable until something breaks horribly forcing me to pin versions.
#
# INSTALL_K3S_SKIP_ENABLE: Don't enable systemd services
# INSTALL_K3S_SKIP_START: Don't start services after installation
curl -sfL https://get.k3s.io | \
  INSTALL_K3S_SKIP_ENABLE=true \
  INSTALL_K3S_SKIP_START=true \
  sh -

# Create kubectl symlink for convenience
sudo ln -sf /usr/local/bin/k3s /usr/local/bin/kubectl

# Create kubeconfig directory structure
sudo mkdir -p /etc/rancher/k3s

# Set up permissions for k3s config directory
sudo chmod 755 /etc/rancher/k3s

# Verify installation (just check binary exists, don't start service)
k3s --version
kubectl version --client

echo "k3s installation complete. Services are installed but NOT enabled."
echo "To start k3s server: sudo systemctl start k3s"
echo "To start k3s agent: sudo systemctl start k3s-agent"
