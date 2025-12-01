# Packer Testing

This directory contains packer configurations and testing tools for building easy-db-lab AMIs.

## Quick Start - Test Scripts Locally

### Using Gradle (Recommended)

```shell
# Test base provisioning scripts
./gradlew testPackerBase

# Test Cassandra provisioning scripts
./gradlew testPackerCassandra

# Run all packer tests
./gradlew testPacker

# Test a specific script
./gradlew testPackerScript -Pscript=cassandra/install/install_cassandra_easy_stress.sh
```

### Using test-script.sh Directly

```shell
cd packer

# Test a single script
./test-script.sh cassandra/install/install_cassandra_easy_stress.sh

# Test another script
./test-script.sh base/install/install_python.sh

# Drop into interactive shell for debugging
./test-script.sh --shell
```

## Using Docker Compose

For interactive testing:

```shell
# Start interactive test environment
docker compose run --rm test

# Inside the container, scripts are mounted at /packer
bash /packer/cassandra/install/install_cassandra_easy_stress.sh
```

Run full provisioning sequences:

```shell
# Test all base provisioning scripts
docker compose up test-base

# Test all cassandra provisioning scripts
docker compose up test-cassandra
```

## Building the Test Image

The test image is built automatically when you run `test-script.sh` or docker-compose commands.

To manually rebuild:

```shell
docker build -t easy-db-lab-packer-test .
```

## Documentation

See [TESTING.md](TESTING.md) for comprehensive testing documentation including:
- Usage examples
- Troubleshooting
- CI integration
- Best practices

## Directory Structure

```
packer/
├── base/                    # Base AMI configuration
│   ├── base.pkr.hcl        # Packer config for base image
│   └── install/            # Base installation scripts
├── cassandra/              # Cassandra AMI configuration
│   ├── cassandra.pkr.hcl  # Packer config for Cassandra image
│   └── install/            # Cassandra installation scripts
├── Dockerfile              # Test environment (mimics Ubuntu 24.04 AMI)
├── docker-compose.yml      # Test orchestration
├── test-script.sh          # Script testing utility
└── TESTING.md              # Comprehensive testing guide
```
