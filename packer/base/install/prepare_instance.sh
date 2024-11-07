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