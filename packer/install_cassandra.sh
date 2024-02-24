#!/bin/bash

# creating cassandra user
sudo useradd -m cassandra

mkdir cassandra
sudo mkdir -p /usr/local/cassandra
sudo mkdir -p /mnt/cassandra
sudo chown -R cassandra:cassandra /mnt/cassandra

lsblk

# shellcheck disable=SC2164
(
cd cassandra

yq '.[].url' /etc/cassandra_versions.yaml | xargs -I{} wget {}

#
# Clone the git repos specified in the yaml file (ending in .git)
# Use the directory name of the org (apache / rustyrazorblade)
# as the directory to clone into
# checkout the branch specified in the yaml file
# do a build and create the tar.gz
#

for f in *.tar.gz;
do
    tar zxvf "$f";
    rm -f "$f";
done

# extracts the version number from the directory name
# this should be refactored to use the version in the yaml file instead
regex="apache-cassandra-([0-9].[0-9*]+(-beta[0-9])?)"

for f in apache-cassandra-*/;
do
  if [[ $f =~ $regex ]]; then
    version="${BASH_REMATCH[1]}"
    echo "Moving $f to $version"
    rm -rf $f/data
    cp "$f"/conf/cassandra.yaml "$f"/conf/cassandra.orig.yaml
    sudo mv "$f" /usr/local/cassandra/$version;
  fi
done

sudo chown -R cassandra:cassandra /usr/local/cassandra
)
