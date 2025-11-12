# Test environment for packer scripts
# Mimics Ubuntu 22.04 AMI environment used by packer
FROM ubuntu:22.04

ENV DEBIAN_FRONTEND=noninteractive

# Install basic dependencies
RUN apt-get update && apt-get install -y \
    sudo \
    curl \
    wget \
    git \
    build-essential \
    ca-certificates \
    gnupg \
    lsb-release \
    software-properties-common \
    && rm -rf /var/lib/apt/lists/*

# Create docker group with standard GID (matches most host systems)
RUN groupadd -g 999 docker || true

# Create ubuntu user (matches packer SSH user) and add to docker group
RUN useradd -m -s /bin/bash ubuntu && \
    echo "ubuntu ALL=(ALL) NOPASSWD:ALL" >> /etc/sudoers && \
    usermod -aG docker ubuntu

# Create cassandra user (used by cassandra services)
RUN useradd -m -s /bin/bash cassandra && \
    echo "cassandra ALL=(ALL) NOPASSWD:ALL" >> /etc/sudoers

# Install Java versions (matches base packer setup)
RUN apt-get update && apt-get install -y \
    openjdk-8-jdk \
    openjdk-11-jdk \
    openjdk-17-jdk \
    && rm -rf /var/lib/apt/lists/*

# Set default Java version to 11 (matches packer base)
RUN update-java-alternatives -s java-1.11.0-openjdk-amd64 || true

# Switch to ubuntu user
USER ubuntu
WORKDIR /home/ubuntu

# Default command
CMD ["/bin/bash"]
