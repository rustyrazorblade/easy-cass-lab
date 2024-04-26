#!/bin/bash

# grab the repo
git clone https://github.com/apache/cassandra-sidecar.git

# build
(
cd cassandra-sidecar
#./gradlew copyJolokia installDist
)

# move into place
sudo mv cassandra-sidecar /usr/local/

