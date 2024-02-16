#!/bin/bash

echo "Downloading version 4"

mkdir cassandra
sudo mkdir -p /usr/local/cassandra

# shellcheck disable=SC2164
(
cd cassandra

# TODO: pull from /etc/cassandra_versions.yaml
wget https://dlcdn.apache.org/cassandra/4.1.3/apache-cassandra-4.1.3-bin.tar.gz
wget https://dlcdn.apache.org/cassandra/4.0.12/apache-cassandra-4.0.12-bin.tar.gz
wget https://dlcdn.apache.org/cassandra/3.0.29/apache-cassandra-3.0.29-bin.tar.gz
wget https://dlcdn.apache.org/cassandra/3.11.16/apache-cassandra-3.11.16-bin.tar.gz
wget https://dlcdn.apache.org/cassandra/5.0-beta1/apache-cassandra-5.0-beta1-bin.tar.gz

for f in *.tar.gz;
do
    tar zxvf "$f";
    rm -f "$f";
done

#regex="apache-cassandra-([0-9].[0-9*])"

# extracts the version number from the directory name
regex="apache-cassandra-([0-9].[0-9*](-beta[0-9])?)"

for f in apache-cassandra-*/;
do
  if [[ $f =~ $regex ]]; then
    version="${BASH_REMATCH[1]}"
    echo "Moving $f to $version"
    sudo mv "$f" /usr/local/cassandra/$version;
  fi
done

)
