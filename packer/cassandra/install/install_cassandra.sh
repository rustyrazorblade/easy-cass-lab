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
        echo "ERROR: No matching version found for prefix $version_prefix"
        return 1
    fi

    # Construct the download URL
    archive="apache-cassandra-$full_version-bin.tar.gz"
    download_url="https://dlcdn.apache.org/cassandra/$full_version/$archive"

    # Download the file
    echo "Downloading Cassandra version $full_version from $download_url..."
    curl -O "$download_url" || {
        echo "ERROR: Failed to download Cassandra version $full_version from $download_url"
        return 1
    }

    # Verify download was successful and file is not empty
    if [[ ! -f "$archive" || ! -s "$archive" ]]; then
        echo "ERROR: Downloaded file $archive is missing or empty for version $full_version"
        return 1
    fi

    echo "Download completed successfully."

    # Extract the archive
    tar zxvf "$archive" || {
        echo "ERROR: Failed to extract $archive for version $full_version"
        return 1
    }

    # Move and verify
    mv "apache-cassandra-$full_version" "$version_prefix" || {
        echo "ERROR: Failed to rename apache-cassandra-$full_version to $version_prefix"
        return 1
    }

    # Verify the directory exists
    if [[ ! -d "$version_prefix" ]]; then
        echo "ERROR: Directory $version_prefix does not exist after extraction and rename"
        return 1
    fi

    return 0
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

# Enable strict error handling
set -euo pipefail
set -x

# Trap errors and report line number
trap 'echo "ERROR: Installation failed at line $LINENO with exit code $?" >&2; exit 1' ERR

# creating cassandra user
sudo useradd -m cassandra
mkdir cassandra

sudo mkdir -p /usr/local/cassandra
sudo mkdir -p /mnt/cassandra/logs
sudo chown -R cassandra:cassandra /mnt/cassandra

# used to skip the expensive checkstyle checks

sudo update-java-alternatives -s java-1.11.0-openjdk-amd64

lsblk

# Change to cassandra directory with error checking
cd cassandra || {
    echo "ERROR: Cannot change to cassandra directory"
    exit 1
}

YAML=/etc/cassandra_versions.yaml
VERSIONS=$(yq '.[].version' "$YAML")
echo "Installing versions: $VERSIONS"

for version in $VERSIONS;
do
  echo "Configuring version: $version"
  export version

  URL=$(yq '.[] | select(.version == env(version)) | .url // ""' "$YAML")
  echo "$URL"

  BRANCH=$(yq '.[] | select(.version == env(version)) | .branch // ""' "$YAML")

  # if $version is set, $URL is blank, and $BRANCH is blank
  if [[ $version != "" && $URL == "" && $BRANCH == "" ]]; then
    download_cassandra_version "$version" || {
        echo "ERROR: download_cassandra_version failed for version $version"
        exit 1
    }

    # check if $version exists in the current directory
    if [[ ! -d $version ]]; then
      echo "ERROR: Failed to download Cassandra version $version - directory not found"
      exit 1
    fi

    sudo mv "$version" "/usr/local/cassandra/$version" || {
        echo "ERROR: Failed to move $version to /usr/local/cassandra/"
        exit 1
    }

  # if a URL is set and ends in .tar.gz, download it
  elif [[ $URL == *.tar.gz ]]; then
    echo "Downloading $URL for version $version"

    wget "$URL" || {
        echo "ERROR: wget failed for version $version from $URL"
        exit 1
    }

    archive_file=$(basename "$URL")

    # Verify download succeeded and file is not empty
    if [[ ! -f "$archive_file" || ! -s "$archive_file" ]]; then
        echo "ERROR: Downloaded file $archive_file is missing or empty for version $version"
        exit 1
    fi

    echo "Extracting $archive_file"
    tar zxvf "$archive_file" || {
        echo "ERROR: Failed to extract $archive_file for version $version"
        exit 1
    }

    rm -f "$archive_file"
    f=$(basename "$URL" -bin.tar.gz)

    # Verify extracted directory exists
    if [[ ! -d "$f" ]]; then
        echo "ERROR: Extracted directory $f not found for version $version"
        exit 1
    fi

    sudo mv "$f" "/usr/local/cassandra/$version" || {
        echo "ERROR: Failed to move $f to /usr/local/cassandra/$version"
        exit 1
    }

  else
    # Clone the git repos specified in the yaml file (ending in .git)
    # Use the directory name of the version field as the dir name
    # as the directory to clone into
    # checkout the branch specified in the yaml file
    # do a build and create the tar.gz
    ANT_FLAGS=$(yq '.[] | select(.version == env(version)) | .ant_flags // ""' "$YAML")
    # all builds work with JDK 11 for now

    echo "Cloning repo for version $version from $URL branch $BRANCH"
    git clone --depth=1 --single-branch --branch "$BRANCH" "$URL" "$version" || {
        echo "ERROR: Git clone failed for version $version from $URL branch $BRANCH"
        exit 1
    }

    # Verify clone was successful
    if [[ ! -d "$version/.git" ]]; then
        echo "ERROR: Git clone incomplete for version $version - .git directory not found"
        exit 1
    fi

    echo "Building version $version with ant"
    (
      cd "$version" || exit 1
      ant realclean && ant -Dno-checkstyle=true $ANT_FLAGS || exit 1
      rm -rf .git
    ) || {
        echo "ERROR: Ant build failed for version $version"
        exit 1
    }

    sudo mv "$version" "/usr/local/cassandra/$version" || {
        echo "ERROR: Failed to move built version $version to /usr/local/cassandra/"
        exit 1
    }
  fi

  # Verify the version was successfully moved to /usr/local/cassandra/
  if [[ ! -d "/usr/local/cassandra/$version" ]]; then
      echo "ERROR: Version $version not found in /usr/local/cassandra/ after installation"
      exit 1
  fi

  # at this point the $version is in place, however it was installed
  # do any general customizations in the below subshell
  echo "Configuring version $version"
  (
      cd "/usr/local/cassandra/$version" || exit 1
      rm -rf data
      cp -R conf conf.orig
      # create a pristine backup of the original conf
      sudo cp conf/cassandra.yaml conf/cassandra.orig.yaml
      cat /tmp/cassandra.in.sh >> bin/cassandra.in.sh
  ) || {
      echo "ERROR: Configuration failed for version $version"
      exit 1
  }

  # Clean up Maven cache to save space
  rm -rf ~/.m2 || true

  echo "✓ Successfully installed and configured version $version"
done

# Final verification - ensure all versions are installed
echo ""
echo "Verifying all versions were installed successfully..."
for version in $VERSIONS; do
    if [[ ! -d "/usr/local/cassandra/$version" ]]; then
        echo "ERROR: Final verification failed - version $version not found in /usr/local/cassandra/"
        exit 1
    fi
    echo "✓ Version $version verified"
done

echo ""
echo "All Cassandra versions installed and verified successfully!"

#rm -rf cassandra
sudo chown -R cassandra:cassandra /usr/local/cassandra
