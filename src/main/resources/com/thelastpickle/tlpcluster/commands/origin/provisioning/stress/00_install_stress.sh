#!/usr/bin/env bash

export DEBIAN_FRONTEND=noninteractive
export APT_KEY_DONT_WARN_ON_DANGEROUS_USAGE=true


echo "Installing tlp-stress"


echo "deb https://dl.bintray.com/thelastpickle/tlp-tools-deb weezy main" | sudo tee -a /etc/apt/sources.list

sudo apt-key adv --keyserver keyserver.ubuntu.com --recv-keys 2895100917357435
sudo apt-get update
sudo DEBIAN_FRONTEND=noninteractive apt-get install -y tlp-stress

