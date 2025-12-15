# Command Reference

Complete reference for all easy-db-lab commands.

## Global Options

| Option | Description |
|--------|-------------|
| `--help, -h` | Shows help information |

## Commands

### init

Initialize a directory for easy-db-lab.

```bash
easy-db-lab init [options]
```

| Option | Description | Default |
|--------|-------------|---------|
| `--cassandra, -c` | Number of Cassandra instances | 3 |
| `--stress, -s` | Number of stress instances | 0 |
| `--instance` | Instance type | c5d.2xlarge |
| `--region` | AWS region | us-west-2 |
| `--monitoring, -m` | Enable monitoring (beta) | false |
| `--up` | Start instances automatically | false |

### up

Start instances.

```bash
easy-db-lab up [options]
```

| Option | Description | Default |
|--------|-------------|---------|
| `--auto-approve, -a, --yes` | Auto approve changes | false |

### down

Shut down a cluster.

```bash
easy-db-lab down [options]
```

| Option | Description | Default |
|--------|-------------|---------|
| `--auto-approve, -a, --yes` | Auto approve changes | false |

### start

Start Cassandra on all nodes via service command.

```bash
easy-db-lab start [options]
```

| Option | Description | Default |
|--------|-------------|---------|
| `--all, -a` | Start all services on all instances | false |
| `--monitoring, -m` | Start services on monitoring instances | false |

### stop

Stop Cassandra on all nodes via service command.

```bash
easy-db-lab stop [options]
```

| Option | Description | Default |
|--------|-------------|---------|
| `--all, -a` | Stop all services on all instances | false |
| `--monitoring, -m` | Stop services on monitoring instances | false |

### install

Install everything on the provisioned instances.

```bash
easy-db-lab install
```

### build

Create a custom named Cassandra build from a working directory.

```bash
easy-db-lab build [options] <path>
```

| Option | Description |
|--------|-------------|
| `-n` | Name of the build |

**Arguments:**

- `<path>` - Path to the Cassandra repository to build

### use

Use a Cassandra build.

```bash
easy-db-lab use [options] <build-name>
```

| Option | Description | Default |
|--------|-------------|---------|
| `--config, -c` | Configuration settings in format `key:value,...` | [] |

### ls

List available builds.

```bash
easy-db-lab ls
```

### clean

Clean up temporary files and artifacts.

```bash
easy-db-lab clean
```

### hosts

Display host information for the current cluster.

```bash
easy-db-lab hosts
```

### server

Start the MCP server for Claude Code integration.

```bash
easy-db-lab server [options]
```

| Option | Description | Default |
|--------|-------------|---------|
| `--port` | Port to listen on | 8080 |

See [MCP Server Integration](../integrations/mcp-server.md) for details.
