#!/bin/bash

####################################################################
##### THE HEADER OF THIS FILE SHOULD BE SHELL FUNCTIONS ONLY #######
### THE INTENT IS TO SAFELY SOURCE THE FILE WITHOUT SIDE EFFECTS ###
####################################################################

## Downloads the latest patch release of a cassandra version
## This saves us from having to update cassandra_versions.yaml
## every time a new patch release is made
download_cassandra_version() {
    # Check if version prefix is provided
    if [ -z "$1" ]; then
        echo "Usage: download_cassandra_version <version-prefix>"
        return 1
    fi

    # Assign the version prefix from the first argument
    version_prefix="$1"

    # Get the list of versions from the Cassandra download page
    versions=$(curl -s https://dlcdn.apache.org/cassandra/ | grep -o 'href="[0-9]\+\.[0-9]\+\.[0-9]\+/\"' | sed 's/href="//' | sed 's/\/"//')

    # Find the latest version that matches the given prefix
    full_version=$(echo "$versions" | grep "^$version_prefix" | sort -V | tail -n 1)

    # Check if a version was found
    if [ -z "$full_version" ]; then
        echo "No matching version found for prefix $version_prefix"
        return 1
    fi

    # Construct the download URL
    archive="apache-cassandra-$full_version-bin.tar.gz"
    download_url="https://dlcdn.apache.org/cassandra/$full_version/$archive"

    # Download the file
    echo "Downloading Cassandra version $full_version from $download_url..."
    curl -O "$download_url"

    # Verify if download was successful
    if [ $? -eq 0 ]; then
        echo "Download completed successfully."
    else
        echo "Failed to download Cassandra version $full_version."
    fi

    tar zxvf $archive
    mv apache-cassandra-$full_version $version_prefix
}

####################################################################
###### DO NOT ADD ANYTHING ABOVE THIS LINE THAT MAKES CHANGES ######
###### TO THE FILE SYSTEM OR DEPENDS ON EXTERNAL RESOURCES #########
###### SHELL FUNCTIONS AND ALIASES ARE OK ##########################
####################################################################

## exit unless INSTALL_CASSANDRA=1
if [ -z "$INSTALL_CASSANDRA" ]; then
    echo "INSTALL_CASSANDRA is not set, exiting."
    return
    exit 0
fi

set -x
# creating cassandra user
sudo useradd -m cassandra
mkdir cassandra

sudo mkdir -p /usr/local/cassandra
sudo mkdir -p /mnt/cassandra/
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

  URL=$(yq '.[] | select(.version == env(version)) | .url // ""' $YAML)
  echo $URL

  BRANCH=$(yq '.[] | select(.version == env(version)) | .branch // ""' $YAML)

  # if $version is set, $URL is blank, and $BRANCH is blank
  if [[ $version != "" && $URL == "" && $BRANCH == "" ]]; then
    download_cassandra_version $version
    # check if $version exists in the current directory
    if [ ! -d $version ]; then
      echo "Failed to download Cassandra version $version"
      exit 1
    fi
    sudo mv $version /usr/local/cassandra/$version

  # if a URL is set and ends in .tar.gz, download it
  elif [[ $URL == *.tar.gz ]]; then
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
    ANT_FLAGS=$(yq '.[] | select(.version == env(version)) | .ant_flags // ""' $YAML)
    # all builds work with JDK 11 for now

    echo "Cloning repo"
    git clone --depth=1 --single-branch --branch $BRANCH $URL $version
    (
      cd $version

      ant realclean && ant -Dno-checkstyle=true $ANT_FLAGS
      rm -rf .git
    )

    sudo mv $version /usr/local/cassandra/$version
  fi
  (
      cd /usr/local/cassandra/$version
      rm -rf data
      cp -R conf conf.orig

      sudo cp conf/cassandra.yaml conf/cassandra.orig.yaml
  )
rm -rf ~/.m2
done
)

#rm -rf cassandra
sudo chown -R cassandra:cassandra /usr/local/cassandra

