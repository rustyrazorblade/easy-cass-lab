#!/bin/bash
set -e

# Ensure non-interactive mode for apt
export DEBIAN_FRONTEND=noninteractive

sudo DEBIAN_FRONTEND=noninteractive apt update -y
sudo DEBIAN_FRONTEND=noninteractive apt install -y make build-essential libssl-dev zlib1g-dev libbz2-dev libreadline-dev libsqlite3-dev wget curl llvm libncursesw5-dev xz-utils tk-dev libxml2-dev libxmlsec1-dev libffi-dev liblzma-dev
curl https://pyenv.run | bash
# add to ~/.bash_profile for use on instance
echo 'export PATH="$HOME/.pyenv/bin:$PATH"' >> ~/.bash_profile
echo 'eval "$(pyenv init --path)"' >> ~/.bash_profile
echo 'eval "$(pyenv virtualenv-init -)"' >> ~/.bash_profile

{
# shellcheck disable=SC2016
echo 'export PATH="$HOME/.pyenv/bin:$PATH"'
# shellcheck disable=SC2016
echo 'eval "$(pyenv init --path)"'
# shellcheck disable=SC2016
echo 'eval "$(pyenv virtualenv-init -)"'
} >> ~/.bash_profile


# now load it in for Packer build
export PATH="$HOME/.pyenv/bin:$PATH"
eval "$(pyenv init --path)"
eval "$(pyenv virtualenv-init -)"
# now install python
pyenv install 2.7.18
pyenv install 3.10.6

# yeah.. this next part a little gross.
# I assume we can keep the pip3 shim around
/home/ubuntu/.pyenv/versions/3.10.6/bin/pip3 install iostat-tool
# shellcheck disable=SC2046
sudo ln -s $(find /home/ubuntu/.pyenv/ -name 'iostat-cli') /usr/local/bin/iostat-cli