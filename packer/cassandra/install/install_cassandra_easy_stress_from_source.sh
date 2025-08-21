#!/bin/bash
set -e

echo "Building cassandra-easy-stress from source..."

# Install git if not present (should already be installed in base image)
if ! command -v git &> /dev/null; then
    sudo apt-get update
    sudo apt-get install -y git
fi

# Clone the Apache cassandra-easy-stress repository
echo "Cloning Apache cassandra-easy-stress repository..."
git clone https://github.com/apache/cassandra-easy-stress.git
cd cassandra-easy-stress

# Build using Gradle wrapper
echo "Building cassandra-easy-stress with Gradle..."
./gradlew shadowJar

# The shadowJar task creates the distribution
./gradlew installDist

# Create installation directory
echo "Installing cassandra-easy-stress to /usr/local/easy-cass-stress..."
sudo rm -rf /usr/local/easy-cass-stress
sudo mkdir -p /usr/local/easy-cass-stress

# Copy the built distribution
# The installDist task creates the distribution in build/install/cassandra-easy-stress
sudo cp -r build/install/cassandra-easy-stress/* /usr/local/easy-cass-stress/

# Ensure scripts are executable
sudo chmod +x /usr/local/easy-cass-stress/bin/*

# Clean up the source directory to save space
echo "Cleaning up build artifacts..."
cd ..
rm -rf cassandra-easy-stress

echo "cassandra-easy-stress installation from source complete!"
echo "Binary available at: /usr/local/easy-cass-stress/bin/cassandra-easy-stress"