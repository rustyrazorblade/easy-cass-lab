#!/bin/bash
set -x
# creating cassandra user
sudo useradd -m cassandra
mkdir cassandra

sudo mkdir -p /usr/local/cassandra
sudo mkdir -p /mnt/cassandra
sudo chown -R cassandra:cassandra /mnt/cassandra

# used to skip the expensive checkstyle checks

sudo update-java-alternatives -s java-1.11.0-openjdk-amd64

lsblk

# shellcheck disable=SC2164
(
cd cassandra

YAML=/etc/cassandra_versions.yaml
VERSIONS=$(yq '.[].version' $YAML)
echo "Installing versions: $VERSIONS"

for version in $VERSIONS;
do
  echo "Configuring version: $version"
  export version
  URL=$(yq ".[] | select(.version == env(version)) | .url" $YAML)
  echo $URL

  # if the URL ends in .tar.gz, download it
  if [[ $URL == *.tar.gz ]]; then
    echo "Downloading $URL"
    wget $URL
    echo $(basename $URL)
    tar zxvf "$(basename $URL)"
    rm -f "$(basename $URL)"
    f=$(basename $URL -bin.tar.gz)
    sudo mv $f /usr/local/cassandra/$version
  else
    # Clone the git repos specified in the yaml file (ending in .git)
    # Use the directory name of the version field as the dir name
    # as the directory to clone into
    # checkout the branch specified in the yaml file
    # do a build and create the tar.gz
    BRANCH=$(yq ".[] | select(.version == env(version)) | .branch" $YAML)

    # all builds work with JDK 11 for now

    echo "Cloning repo"
    git clone --depth=1 --single-branch --branch $BRANCH $URL $version
    (
      cd $version
      ant -Dno-checkstyle=true
      rm -rf .git
    )

    sudo mv $version /usr/local/cassandra/$version
  fi
  (
      cd /usr/local/cassandra/$version
      rm -rf data
      sudo cp conf/cassandra.yaml conf/cassandra.orig.yaml
  )
done
)

#rm -rf cassandra
sudo chown -R cassandra:cassandra /usr/local/cassandra

