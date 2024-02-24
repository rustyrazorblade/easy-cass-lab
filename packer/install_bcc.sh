sudo apt update
sudo apt purge bpfcc-tools libbpfcc python3-bpfcc
wget https://github.com/iovisor/bcc/releases/download/v0.25.0/bcc-src-with-submodule.tar.gz
tar xf bcc-src-with-submodule.tar.gz
cd bcc/
#sudo apt install -y python-is-python3
#sudo apt install -y bison build-essential cmake flex git libedit-dev   libllvm11
# llvm-11-dev libclang-11-dev zlib1g-dev libelf-dev libfl-dev python3-distutils

sudo apt install -y zip bison build-essential cmake flex git libedit-dev \
  libllvm14 llvm-14-dev libclang-14-dev python3 zlib1g-dev libelf-dev libfl-dev python3-setuptools \
  liblzma-dev libdebuginfod-dev arping netperf iperf

# from https://github.com/iovisor/bcc/blob/master/INSTALL.md
# Section Debian - Source

# Before you begin
#apt-get update
# According to https://packages.debian.org/source/sid/bpfcc,
# BCC build dependencies:
#sudo apt-get install arping bison clang-format cmake dh-python \
#  dpkg-dev pkg-kde-tools ethtool flex inetutils-ping iperf \
#  libbpf-dev libclang-dev libclang-cpp-dev libedit-dev libelf-dev \
#  libfl-dev libzip-dev linux-libc-dev llvm-dev libluajit-5.1-dev \
#  luajit python3-netaddr python3-pyroute2 python3-setuptools python3

#
mkdir build
cd build/
cmake ..
make
sudo make install
cmake -DPYTHON_CMD=python3 .. # build python3 binding
pushd src/python/
make
sudo make install
popd


#cmake -DCMAKE_INSTALL_PREFIX=/usr -DPYTHON_CMD=python3 ..
#make
#sudo make install


