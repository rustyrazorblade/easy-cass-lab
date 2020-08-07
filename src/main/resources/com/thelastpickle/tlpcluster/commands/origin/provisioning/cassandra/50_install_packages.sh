#!/usr/bin/env bash

set -x
echo "Installing all deb packages"

for d in $(ls *.deb)
do
    DEBIAN_FRONTEND=noninteractive sudo apt-get install -y ./${d}
done

echo "Finished installing deb packages"