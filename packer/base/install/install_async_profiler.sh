#!/bin/bash

# Get the architecture using uname
cpu_arch=$(uname -m)

# Set ARCH based on the CPU architecture
if [[ "$cpu_arch" == "x86_64" ]]; then
    ARCH="x64"
elif [[ "$cpu_arch" == "aarch64" ]]; then
    ARCH="arm64"
else
    echo "Unsupported architecture: $cpu_arch"
    exit 1
fi

echo "ARCH is set to: $ARCH"

RELEASE_VERSION="3.0"
RELEASE="async-profiler-${RELEASE_VERSION}-linux-${ARCH}"
ARCHIVE="${RELEASE}.tar.gz"

sudo sysctl kernel.perf_event_paranoid=1
sudo sysctl kernel.kptr_restrict=0
wget "https://github.com/async-profiler/async-profiler/releases/download/v${RELEASE_VERSION}/${ARCHIVE}"
tar zxvf $ARCHIVE
sudo mv $RELEASE /usr/local/async-profiler