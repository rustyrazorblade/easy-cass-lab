#!/usr/bin/env bash

# Mount the volume that is going to be used to store the data
echo "Mounting data volume "

MOUNT_POINT=/var/lib/cassandra/

DEVICES=( "/dev/nvme1n1" "/dev/xvdb" )

for TARGET_DEVICE in "${DEVICES[@]}"; do
    echo "Checking $TARGET_DEVICE"

    if [[ -b "$TARGET_DEVICE" ]]
    then
	    echo "Creating volume from $TARGET_DEVICE"
      sudo mkfs.xfs -s size=4096 -f /dev/xvdb
      sudo mkdir -p ${MOUNT_POINT}
	    sudo mount $TARGET_DEVICE $MOUNT_POINT
	    sudo blockdev --setra 8 $TARGET_DEVICE
      sudo chmod 755 ${MOUNT_POINT}
      break 2
    fi
    echo "Next"

done
