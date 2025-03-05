#!/bin/bash

# grab the repo
git clone https://github.com/apache/cassandra-sidecar.git

# build
(
cd cassandra-sidecar
./gradlew copyJolokia installDist
sudo cp -r build/install/apache-cassandra-sidecar /usr/local/cassandra-sidecar
)


