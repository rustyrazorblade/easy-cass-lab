#!/usr/bin/env bash
set -x
export DEBIAN_FRONTEND=noninteractive
export APT_KEY_DONT_WARN_ON_DANGEROUS_USAGE=true


echo "Installing tlp-stress"

VERSION="4.0.0"
sudo DEBIAN_FRONTEND=noninteractive apt-get install -y jq
echo "Installing tlp-stress stable from the repo"
wget https://bintray.com/rustyrazorblade/tlp-tools-deb/download_file?file_path=tlp-stress_${VERSION}_all.deb -O tlp-stress_${VERSION}_all.deb
sudo DEBIAN_FRONTEND=noninteractive apt-get install -y ./tlp-stress_${VERSION}_all.deb



