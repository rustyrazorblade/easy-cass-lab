#!/usr/bin/env bash

###### CONFIGURATION ######

#export DISK=/dev/nvme0n1
export DISK=/dev/xvdb
export READAHEAD=8

###########################


###### system settings #####

sudo sysctl kernel.perf_event_paranoid=1
sudo sysctl kernel.kptr_restrict=0

echo 0 > /proc/sys/vm/zone_reclaim_mode

cat <<EOF | sudo tee /etc/security/limits.d/cassandra.conf
cassandra soft memlock unlimited
cassandra hard memlock unlimited
cassandra soft nofile 100000
cassandra hard nofile 100000
cassandra soft nproc 32768
cassandra hard nproc 32768
cassandra - as unlimited
EOF

cat <<EOF | sudo tee /etc/sysctl.d/60-cassandra.conf
vm.max_map_count = 1048575
EOF

sudo swapoff --all

sudo sysctl -p

########


#export DISK=/dev/xvdb

# i3 instances have NVMe drives

sudo mkdir -p /mnt/cassandra

FS_TYPE=$(sudo blkid -o value -s TYPE $DISK )

if [ -z "$FS_TYPE" ]; then
  echo "No file system found on $DISK. Formatting with XFS."
  sudo mkfs.xfs $DISK
else
  echo "File system found on $DISK. Not formatting."
fi

sudo mount | grep $DISK

if [ $? -eq 0 ]; then
    echo "$1 is mounted already."
else
    echo "$1 is not mounted yet, mounting."
    sudo mount $DISK /mnt/cassandra
fi

sudo chown cassandra:cassandra /mnt/cassandra
sudo blockdev --setra $READAHEAD $DISK

echo

# Here's some other ideas

# multiple drives in a RAID
# multiple drives using JBOD

