#!/usr/bin/env bash

###### CONFIGURATION ######
## ANY VARIABLE NEEDED IN THIS SCRIPT
## SHOULD BE SET IN THIS BLOCK

export READAHEAD=8

DISK=""

for VOL in nvme0n1 nvme1n1 xvdb; do
  export VOL
  echo "Checking $VOL"
  TMP=$(lsblk -o NAME,MOUNTPOINTS -J | yq '.blockdevices[] | select(.name == env(VOL)) | has("children")')
  echo $TMP

  if [[ "${TMP}" == "false" ]]; then
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

sudo sysctl -p /etc/sysctl.d/60-cassandra.conf
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
chmod 777 /mnt/cassandra/artifacts

sudo mkdir -p /mnt/cassandra/import
sudo mkdir -p /mnt/cassandra/logs/sidecar
sudo mkdir -p /mnt/cassandra/saved_caches

sudo chown -R cassandra:cassandra /mnt/cassandra

sudo mkdir -p /mnt/cassandra/tmp
sudo chmod 777 /mnt/cassandra/tmp/

# enable cap_perfmon for all JVMs to allow for off-cpu profiling
sudo find /usr/lib/jvm/ -type f -name 'java' -exec setcap "cap_perfmon,cap_sys_ptrace,cap_syslog=ep" {} \;
