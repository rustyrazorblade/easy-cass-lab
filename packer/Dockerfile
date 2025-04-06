FROM ubuntu:22.04
ENV DEBIAN_FRONTEND noninteractive
RUN apt-get update && apt-get install -y ubuntu-server
RUN apt-get install -y openjdk-8-jdk openjdk-8-dbg openjdk-11-jdk openjdk-11-dbg openjdk-17-jdk openjdk-17-dbg
COPY cassandra/cassandra_versions.yaml /etc/cassandra_versions.yaml

