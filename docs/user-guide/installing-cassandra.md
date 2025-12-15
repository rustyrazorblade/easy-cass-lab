# Installing Cassandra

There are two ways to install Cassandra on your instances: using a released build or a custom build.

## Supported Versions

easy-db-lab supports the following Cassandra versions:

| Version | Java | Notes |
|---------|------|-------|
| 3.0 | 8 | Legacy support |
| 3.11 | 8 | Stable release |
| 4.0 | 11 | First 4.x release |
| 4.1 | 11 | Current LTS |
| 5.0 | 11 | Latest stable |
| 5.0-HEAD | 11 | Nightly build from trunk |
| trunk | 17 | Development branch |

To list versions installed on your cluster:

```bash
easy-db-lab ls
```

## The Easy Way - Use a Released Build

The easiest path to getting a cluster up and running:

```bash
easy-db-lab use 3.11.4
easy-db-lab install
easy-db-lab start
```

Simply replace `3.11.4` with the release version you want to use.

## The Hard Way - Use a Custom Build

To install Cassandra with a custom build, follow these steps:

1. Build the version you need and give it a build name (optional)
2. Tell easy-db-lab to use the custom build

### Step 1: Create a Build

If you have no builds, run the following:

```bash
easy-db-lab build -n BUILD_NAME /path/to/repo
```

Where:

- `BUILD_NAME` - Name you want to give the build (e.g., `my-build-cass-4.0`)
- `/path/to/repo` - Full path to clone of the Cassandra repository

### Step 2: Use the Build

If you already have a build that you would like to use:

```bash
easy-db-lab use BUILD_NAME
```

This will copy the binaries and configuration files to the `provisioning/cassandra` directory in your `easy-db-lab` repository.

### Custom Provisioning Scripts

The `provisioning` directory contains files that can be used to set up your instances. If you want to install other binaries or perform other operations during provisioning, you can add them to the `provisioning/cassandra` directory.

!!! note
    Any new scripts you add should be prefixed with a number which is used to determine the order they are executed by the `install.sh` script.

### Step 3: Install

To provision the instances:

```bash
easy-db-lab install
```

This will push the contents of the `provisioning/cassandra` directory up to each of the instances and install Cassandra on them.

## Listing Available Builds

To see what builds are available:

```bash
easy-db-lab ls
```

## Starting and Stopping Cassandra

After installation, manage the Cassandra service:

```bash
# Start Cassandra on all nodes
easy-db-lab start

# Stop Cassandra on all nodes
easy-db-lab stop
```

## Cassandra Sidecar

The [Apache Cassandra Sidecar](https://github.com/apache/cassandra-sidecar) is automatically installed and started alongside Cassandra. The sidecar provides:

- REST API for Cassandra operations
- S3 import/restore capabilities
- Streaming data operations
- Metrics collection (Prometheus-compatible)

### Sidecar Access

The sidecar runs on port `9043` on each Cassandra node:

```bash
# Check sidecar health
curl http://<cassandra-node-ip>:9043/api/v1/__health
```

### Sidecar Management

The sidecar is managed via systemd and starts automatically with Cassandra:

```bash
# Check sidecar status on a node
ssh db0 sudo systemctl status cassandra-sidecar

# Restart sidecar
ssh db0 sudo systemctl restart cassandra-sidecar
```

### Configuration

The sidecar configuration is located at `/etc/cassandra-sidecar/cassandra-sidecar.yaml` on each node. Key settings include:

- Cassandra connection details
- Data directory paths
- Traffic shaping and throttling
- S3 integration settings
