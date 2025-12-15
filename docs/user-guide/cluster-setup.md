# Cluster Setup

This guide covers initializing and launching your easy-db-lab cluster.

## Initialize

The tool uses the current working directory as a working space for all cluster configs and artifacts.

```bash
easy-db-lab init
```

This creates a `terraform.tf.json` file with your cluster configuration.

### Init Options

| Option | Description | Default |
|--------|-------------|---------|
| `--cassandra, -c` | Number of Cassandra instances | 3 |
| `--stress, -s` | Number of stress instances | 0 |
| `--instance` | Instance type | c5d.2xlarge |
| `--region` | AWS region | us-west-2 |
| `--monitoring, -m` | Enable monitoring (beta) | false |
| `--cpu` | CPU architecture (x86_64 or arm64) | x86_64 |
| `--s3-bucket` | S3 bucket for ClickHouse storage | none |

### ARM64 Support

For Graviton instances, use the `--cpu arm64` flag:

```bash
easy-db-lab init --cpu arm64
```

## Launch Instances

After initializing, launch your instances:

```bash
easy-db-lab up
```

Terraform will ask you to type `yes` to confirm. Use `--yes` to skip the prompt:

```bash
easy-db-lab up --yes
```

### Environment Setup

After launching, source the environment file to get helpful aliases:

```bash
source env.sh
```

This sets up SSH, SCP, SFTP, and rsync to use the cluster's SSH configuration. See [Shell Aliases](shell-aliases.md) for all available shortcuts.

## Shut Down

To destroy the cluster infrastructure:

```bash
easy-db-lab down
```

Add `--yes` to auto-approve:

```bash
easy-db-lab down --yes
```
