#!/bin/bash
set -e

echo "Building cassandra-easy-stress from source..."

# Install git if not present (should already be installed in base image)
if ! command -v git &> /dev/null; then
    sudo apt-get update
    sudo apt-get install -y git
fi

# Detect CPU architecture for Java path
cpu_arch=$(uname -m)
if [[ "$cpu_arch" == "x86_64" ]]; then
    ARCH="amd64"
elif [[ "$cpu_arch" == "aarch64" ]]; then
    ARCH="arm64"
else
    echo "Unsupported architecture: $cpu_arch"
    exit 1
fi

# Save current Java version to restore later
CURRENT_JAVA=$(update-alternatives --query java | grep 'Value:' | awk '{print $2}')
echo "Current Java: $CURRENT_JAVA"

# Switch to Java 17 for the build (cassandra-easy-stress requires Java 17)
echo "Switching to Java 17 for build..."
sudo update-java-alternatives -s java-1.17.0-openjdk-$ARCH

# Verify Java version
java -version

# Clone the Apache cassandra-easy-stress repository
echo "Cloning Apache cassandra-easy-stress repository..."
git clone https://github.com/apache/cassandra-easy-stress.git
cd cassandra-easy-stress

# Build using Gradle wrapper with Java 17
echo "Building cassandra-easy-stress with Gradle..."
./gradlew shadowJar

# The shadowJar task creates the distribution
./gradlew installDist

# Create installation directory
echo "Installing cassandra-easy-stress to /usr/local/cassandra-easy-stress..."
sudo rm -rf /usr/local/cassandra-easy-stress
sudo mkdir -p /usr/local/cassandra-easy-stress

# Copy the built distribution
# The installDist task creates the distribution in build/install/cassandra-easy-stress
sudo cp -r build/install/cassandra-easy-stress/* /usr/local/cassandra-easy-stress/

# Ensure scripts are executable
sudo chmod +x /usr/local/cassandra-easy-stress/bin/*

# Clean up the source directory to save space
echo "Cleaning up build artifacts..."
cd ..
rm -rf cassandra-easy-stress

echo "cassandra-easy-stress installation from source complete!"
echo "Binary available at: /usr/local/cassandra-easy-stress/bin/cassandra-easy-stress"
