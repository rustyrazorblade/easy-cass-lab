# ClickHouse

easy-db-lab supports deploying ClickHouse clusters on Kubernetes for analytics workloads alongside your Cassandra cluster.

## Overview

ClickHouse is deployed as a StatefulSet on K3s with ClickHouse Keeper for distributed coordination. The deployment requires a minimum of 3 nodes.

## Starting ClickHouse

To deploy a ClickHouse cluster:

```bash
easy-db-lab clickhouse start
```

### Options

| Option | Description | Default |
|--------|-------------|---------|
| `--timeout` | Seconds to wait for pods to be ready | 300 |
| `--skip-wait` | Skip waiting for pods to be ready | false |
| `--replicas` | Number of ClickHouse server replicas | Number of db nodes |

### Example with Custom Settings

```bash
easy-db-lab clickhouse start --replicas 3 --timeout 600
```

## Checking Status

To check the status of your ClickHouse cluster:

```bash
easy-db-lab clickhouse status
```

This displays:

- Pod status and health
- Access URLs for the Play UI and HTTP interface
- Native protocol connection details

## Accessing ClickHouse

After deployment, ClickHouse is accessible via:

| Interface | URL/Port | Description |
|-----------|----------|-------------|
| Play UI | `http://<db-node-ip>:8123/play` | Interactive web query interface |
| HTTP API | `http://<db-node-ip>:8123` | REST API for queries |
| Native Protocol | `<db-node-ip>:9000` | High-performance binary protocol |

## Storage Policies

ClickHouse is configured with two storage policies. You select the policy when creating a table using the `SETTINGS storage_policy` clause.

### Policy Comparison

| Aspect | `local` | `s3_main` |
|--------|---------|-----------|
| **Storage Location** | Local NVMe disks | S3 bucket with 10GB local cache |
| **Performance** | Best latency, highest throughput | Higher latency, cache-dependent |
| **Capacity** | Limited by disk size | Virtually unlimited |
| **Cost** | Included in instance cost | S3 storage + request costs |
| **Data Persistence** | Lost when cluster is destroyed | Persists independently |
| **Best For** | Benchmarks, low-latency queries | Large datasets, cost-sensitive workloads |

### Local Storage (`local`)

The default policy stores data on local NVMe disks attached to the database nodes. This provides the best performance for latency-sensitive workloads.

```sql
CREATE TABLE my_table (...)
ENGINE = MergeTree()
ORDER BY id
SETTINGS storage_policy = 'local';
```

If you omit the `storage_policy` setting, tables use local storage by default.

**When to use local storage:**

- Performance benchmarking where latency matters
- Temporary or experimental datasets
- Workloads with predictable data sizes that fit on local disks
- When you don't need data to persist after cluster teardown

### S3 Storage (`s3_main`)

The S3 policy stores data in your configured S3 bucket with a 10GB local cache for frequently accessed data. This is ideal for large datasets where storage cost matters more than latency.

**Prerequisite**: Your cluster must be initialized with an S3 bucket. Set this during `init`:

```bash
easy-db-lab init my-cluster --s3-bucket my-clickhouse-data
```

Then create tables with S3 storage:

```sql
CREATE TABLE my_table (...)
ENGINE = ReplicatedMergeTree('/clickhouse/tables/{shard}/default/my_table', '{replica}')
ORDER BY id
SETTINGS storage_policy = 's3_main';
```

**When to use S3 storage:**

- Large analytical datasets (terabytes+)
- Data that should persist across cluster restarts
- Cost-sensitive workloads where storage cost > compute cost
- Sharing data between multiple clusters

**How the cache works:**

- Hot (frequently accessed) data is cached locally for fast reads
- Cold data is fetched from S3 on demand
- Cache is automatically managed by ClickHouse
- First query on cold data will be slower; subsequent queries use cache

## Stopping ClickHouse

To remove the ClickHouse cluster:

```bash
easy-db-lab clickhouse stop
```

This removes all ClickHouse pods, services, and associated resources from Kubernetes.

## Monitoring

ClickHouse metrics are automatically integrated with the observability stack:

- **Grafana Dashboard**: Pre-configured dashboard for ClickHouse metrics
- **Metrics Port**: `9363` for Prometheus-compatible metrics
- **Logs Dashboard**: Dedicated dashboard for ClickHouse logs

## Architecture

The ClickHouse deployment includes:

- **ClickHouse Server**: StatefulSet with configurable replicas
- **ClickHouse Keeper**: 3-node cluster for distributed coordination (ZooKeeper-compatible)
- **Services**: Headless services for internal communication
- **ConfigMaps**: Server and Keeper configuration

### Ports

| Port | Purpose |
|------|---------|
| 8123 | HTTP interface |
| 9000 | Native protocol |
| 9009 | Inter-server communication |
| 9363 | Metrics |
| 2181 | Keeper client |
| 9234 | Keeper Raft |
