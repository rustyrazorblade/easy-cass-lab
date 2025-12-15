# Shell Aliases

After running `source env.sh`, you get access to several helpful aliases and functions for managing your cluster.

## SSH Aliases

SSH aliases for all Cassandra nodes are automatically created as `c0`-`cN`. The `ssh` command is not required. For example:

```bash
c0 nodetool status
```

This runs `nodetool status` on the first Cassandra node.

## Cluster Management Functions

| Command | Description |
|---------|-------------|
| `c-all` | Executes a command on every node in the cluster sequentially |
| `c-start` | Starts Cassandra on all nodes |
| `c-restart` | Restarts Cassandra on all nodes (not a graceful operation) |
| `c-status` | Executes `nodetool status` on db0 |
| `c-tpstats` | Executes `nodetool tpstats` on all nodes |
| `c-collect-artifacts` | Collects metrics, nodetool output, and system information |

## Examples

### Run a command on all nodes

```bash
c-all "df -h"
```

### Check cluster status

```bash
c-status
```

### Collect artifacts for performance testing

```bash
c-collect-artifacts my-test-run
```

This is useful when doing performance testing to capture the state of the system at a given moment.

## Graceful Rolling Restarts

For true rolling restarts, we recommend using [cstar](https://github.com/spotify/cstar) instead of `c-restart`.
