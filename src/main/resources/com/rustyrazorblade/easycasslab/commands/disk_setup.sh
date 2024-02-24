#!/usr/bin/env bash

sudo mkdir -p /mnt/cassandra

FS_TYPE=$(sudo blkid -o value -s TYPE "/dev/xvdb")

if [ -z "$FS_TYPE" ]; then
  echo "No file system found on /dev/xvdb. Formatting with XFS."
  sudo mkfs.xfs /dev/xvdb
else
  echo "File system found on /dev/xvdb. Not formatting."
fi

sudo mount | grep "/dev/xvdb"

if [ $? -eq 0 ]; then
    echo "$1 is mounted already."
else
    echo "$1 is not mounted yet, mounting."
    sudo mount /dev/xvdb /mnt/cassandra
fi

sudo chown cassandra:cassandra /mnt/cassandra
# Here's some other ideas

# multiple drives in a RAID
# multiple drives using JBOD

