#!/bin/bash

# needed early on before we do anything with /mnt
if mountpoint -q /mnt; then
  echo "/mnt is a mount point."
  sudo umount -l -f /mnt
else
  echo "/mnt is not a mount point."
fi

sudo apt update
sudo apt upgrade -y
sudo apt update

sudo apt install -y wget sysstat unzip ripgrep ant ant-optional tree zfsutils-linux  nicstat

cpu_arch=$(uname -m)
# Set ARCH based on the CPU architecture
if [[ "$cpu_arch" == "x86_64" ]]; then
    apt install -y cpuid
elif [[ "$cpu_arch" == "aarch64" ]]; then
  echo "No additional packages needed for ARM64"
else
    echo "Unsupported architecture: $cpu_arch"
    exit 1
fi


