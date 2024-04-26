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
    ANT_FLAGS=$(yq '.[] | select(.version == env(version)) | .ant_flags // ""' $YAML)
    # all builds work with JDK 11 for now

    echo "Cloning repo"
    git clone --depth=1 --single-branch --branch $BRANCH $URL $version
    (
      cd $version

      ant -Dno-checkstyle=true $ANT_FLAGS
      rm -rf .git
    )

    sudo mv $version /usr/local/cassandra/$version
  fi
  (
      cd /usr/local/cassandra/$version
      rm -rf data
      cp -R conf conf.orig

      sudo cp conf/cassandra.yaml conf/cassandra.orig.yaml

      # hard link the JVM options if it's not jvm.options
      # this will allow the user to overwrite jvm.options
      # we can have consistency across versions this way
      # it's easier then trying to deal with patching the file
      # because with patching we need to identify all the GC options
      # specific to each GC algo
      # and make sure they're applied exclusively, which isn't really
      # that big of a win for the amount of work I need to do.
      JVM_OPTIONS=$(yq ".[] | select(.version == env(version)) | .jvm_options" $YAML)
      # back it up

      if [[ $JVM_OPTIONS != "jvm.options" ]]; then
        echo "Linking $JVM_OPTIONS to jvm.options"
        ln -f conf/$JVM_OPTIONS conf/jvm.options
      else
        echo "jvm.options exists, not linking."
      fi
  )
done
)

#rm -rf cassandra
sudo chown -R cassandra:cassandra /usr/local/cassandra

