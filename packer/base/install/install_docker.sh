#!/bin/bash
set -ex

# Ensure non-interactive mode for apt
export DEBIAN_FRONTEND=noninteractive

# Install Docker on Ubuntu 22.04
# Based on official Docker installation guide

# Update apt package index
sudo DEBIAN_FRONTEND=noninteractive apt-get update -y

# Install packages to allow apt to use a repository over HTTPS
sudo DEBIAN_FRONTEND=noninteractive apt-get install -y \
    ca-certificates \
    curl \
    gnupg \
    lsb-release

# Add Docker's official GPG key
sudo mkdir -p /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg

# Set up the repository
echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
  $(lsb_release -cs) stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

# Update apt package index again
sudo DEBIAN_FRONTEND=noninteractive apt-get update -y

# Install Docker Engine
sudo DEBIAN_FRONTEND=noninteractive apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

# Add ubuntu user to docker group
sudo usermod -aG docker ubuntu

# Enable Docker to start on boot
sudo systemctl enable docker

# Verify installation
sudo docker run --rm hello-world