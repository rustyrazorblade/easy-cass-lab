#!/bin/bash
set -e

# Ensure non-interactive mode for apt
export DEBIAN_FRONTEND=noninteractive

# Add deadsnakes PPA for additional Python versions
sudo DEBIAN_FRONTEND=noninteractive add-apt-repository ppa:deadsnakes/ppa -y
sudo DEBIAN_FRONTEND=noninteractive apt update -y

# Install Python 2.7 and 3.10 with development packages
sudo DEBIAN_FRONTEND=noninteractive apt install -y python2.7 python2.7-dev
sudo DEBIAN_FRONTEND=noninteractive apt install -y python3.10 python3.10-dev python3.10-venv python3-pip

# Setup update-alternatives for python version management
# Priority 1 for Python 2.7 (lower priority)
sudo update-alternatives --install /usr/bin/python python /usr/bin/python2.7 1
# Priority 2 for Python 3.10 (higher priority, becomes default)
sudo update-alternatives --install /usr/bin/python python /usr/bin/python3.10 2

# Set Python 3.10 as the default
sudo update-alternatives --set python /usr/bin/python3.10

# Install pip for Python 2.7 (needed for Cassandra 3.x compatibility)
# Python 2.7 pip is deprecated but still needed for legacy Cassandra versions
curl https://bootstrap.pypa.io/pip/2.7/get-pip.py -o /tmp/get-pip.py
sudo python2.7 /tmp/get-pip.py
rm /tmp/get-pip.py

# Install iostat-tool using pip3
pip3 install iostat-tool

# Create symlink for iostat-cli
# Find the installed location and create symlink
sudo ln -sf $(which iostat-cli) /usr/local/bin/iostat-cli
