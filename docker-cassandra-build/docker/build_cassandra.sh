#!/usr/bin/env bash

BUILD_DIRECTORY=/workspace
set -x

# external mount
cd /cassandra

[[ -d /build ]] && rm -rf /build

mkdir /build

rsync -av --exclude-from=/excludes.txt . $BUILD_DIRECTORY

cd $BUILD_DIRECTORY

export JAVA8_HOME=/usr/lib/jvm/java-1.8.0-openjdk-amd64
export JAVA_HOME=/usr/lib/jvm/java-1.8.0-openjdk-amd64
apt update
# Install build dependencies and build package
echo "y" | mk-build-deps --install
dpkg-buildpackage -uc -us

cp ../cassandra*all.deb /builds/deb/
cp -R debian/cassandra/etc/cassandra/* /builds/conf/

