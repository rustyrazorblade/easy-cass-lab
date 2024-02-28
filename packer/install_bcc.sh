#!/usr/bin/env bash

BCC_VERSION=0.29.1

sudo apt update
sudo apt purge bpfcc-tools libbpfcc python3-bpfcc
wget https://github.com/iovisor/bcc/releases/download/v${BCC_VERSION}/bcc-src-with-submodule.tar.gz
tar xf bcc-src-with-submodule.tar.gz
cd bcc/


sudo apt install -y zip bison build-essential cmake flex git libedit-dev \
  libllvm14 llvm-14-dev libclang-14-dev python3 zlib1g-dev libelf-dev libfl-dev python3-setuptools \
  liblzma-dev libdebuginfod-dev arping netperf iperf

# from https://github.com/iovisor/bcc/blob/master/INSTALL.md

sudo ln -s /usr/bin/python3 /usr/bin/python
mkdir build
cd build/
cmake ..
make
sudo make install
cmake -DPYTHON_CMD=/usr/bin/python3 .. # build python3 binding
pushd src/python/
make
sudo make install
popd

# cleanup
cd
sudo rm -rf bcc bcc-src-with-submodule.tar.gz

