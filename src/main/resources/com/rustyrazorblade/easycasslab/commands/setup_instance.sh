#!/usr/bin/env bash

###### CONFIGURATION ######
## ANY VARIABLE NEEDED IN THIS SCRIPT
## SHOULD BE SET IN THIS BLOCK

export READAHEAD=8

DISK=""

for VOL in nvme1n1 xvdb; do
  export VOL
  echo "Checking $VOL"
  TMP=$(lsblk -o NAME,MOUNTPOINTS -J | yq '.blockdevices[] | select(.name == env(VOL)) | length')
  if [ -n "$TMP" ]; then
    DISK="/dev/$VOL"
    break
  fi
done

echo "Using disk: $DISK"

## END CONFIGURATION ###
###########################

###### SYSTEM SETTINGS // OS TUNINGS #####

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

sudo mkdir -p /mnt/cassandra

if [[ -n "$DISK" ]]; then
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

  sudo blockdev --setra $READAHEAD $DISK
fi


sudo mkdir -p /mnt/cassandra/artifacts
sudo chown -R cassandra:cassandra /mnt/cassandra

sudo mkdir /mnt/cassandra/tmp
sudo chmod 777 /mnt/cassandra/tmp/
