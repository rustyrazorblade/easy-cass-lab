#!/bin/bash

set -x

mkdir fio
cd fio
wget https://github.com/axboe/fio/archive/refs/tags/fio-3.36.zip
unzip fio-*.zip

( # subshell
cd fio-fio*
./configure
make
sudo make install
)

rm -rf fio