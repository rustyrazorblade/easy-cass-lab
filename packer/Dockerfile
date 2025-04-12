FROM ubuntu:22.04
ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get update && apt-get install -y ubuntu-server curl sudo
RUN useradd -m cassandra

RUN apt-get install -y openjdk-8-jdk openjdk-8-dbg openjdk-11-jdk openjdk-11-dbg openjdk-17-jdk openjdk-17-dbg

RUN curl -fsSL https://apt.releases.hashicorp.com/gpg | apt-key add - \
    && apt-add-repository "deb [arch=amd64] https://apt.releases.hashicorp.com $(lsb_release -cs) main" \
    && apt-get update && apt-get install -y packer

COPY cassandra/cassandra_versions.yaml /etc/cassandra_versions.yaml

