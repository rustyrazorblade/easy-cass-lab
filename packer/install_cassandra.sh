#!/bin/bash

echo "Downloading version 4"

mkdir cassandra

# shellcheck disable=SC2164
(
cd cassandra

wget https://dlcdn.apache.org/cassandra/4.1.3/apache-cassandra-4.1.3-bin.tar.gz
wget https://dlcdn.apache.org/cassandra/4.0.11/apache-cassandra-4.0.11-bin.tar.gz
wget https://dlcdn.apache.org/cassandra/3.0.29/apache-cassandra-3.0.29-bin.tar.gz
wget https://dlcdn.apache.org/cassandra/3.11.16/apache-cassandra-3.11.16-bin.tar.gz
wget https://dlcdn.apache.org/cassandra/5.0-beta1/apache-cassandra-5.0-beta1-bin.tar.gz

for f in *.tar.gz;
do
    tar zxvf "$f";
    rm -f "$f";
done

for f in apache-cassandra-*/;
do
    sudo mv "$f" /usr/local/;
done
)

#tar zxvf *.tar.gz
#rm -f *.tar.gz
#sudo mv * /usr/local/