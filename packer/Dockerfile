FROM ubuntu:22.04

ENV DEBIAN_FRONTEND=noninteractive

# Update apt package list and install Python 3 and Bash.
# Ubuntu's default shell is Bash, but we ensure it's the latest version.
RUN apt-get update && apt-get install -y --no-install-recommends \
    bash \
    python3 \
    python3-pip \
    wget \
    git \
    ant \
    ant-optional \
    sysstat \
    unzip \
    ripgrep \
    && rm -rf /var/lib/apt/lists/*

# Set Python 3 as the default python
RUN ln -s /usr/bin/python3 /usr/bin/python
RUN wget https://github.com/mikefarah/yq/releases/download/v4.41.1/yq_linux_amd64 -O /usr/local/bin/yq && chmod +x /usr/local/bin/yq

# sudo hack
RUN echo "#!/bin/bash\n\$@" > /usr/bin/sudo
RUN chmod +x /usr/bin/sudo


# Set the working directory in the container
WORKDIR /app

# Specify the default command for the container
CMD ["bash"]
