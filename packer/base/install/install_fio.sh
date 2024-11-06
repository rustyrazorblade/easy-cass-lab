#!/bin/bash

set -x

VERSION="3.37"

mkdir fio
cd fio
wget "https://github.com/axboe/fio/archive/refs/tags/fio-${VERSION}.zip"
unzip fio-*.zip

( # subshell
cd fio-fio*
./configure
make
sudo make install
)

rm -rf fio