# MCP Server Integration with Claude Code

easy-db-lab includes a Model Context Protocol (MCP) server that allows seamless integration with Claude Code. This enables Claude to directly interact with your easy-db-lab clusters and perform operations through a standardized protocol.

## Starting the MCP Server

To start the MCP server, run:

```bash
easy-db-lab server --port 8888
```

This will start the MCP server on port 8888 (you can use any available port).

## Adding to Claude Code

Once the MCP server is running, you can add it to Claude Code using:

```bash
claude mcp add --transport sse easy-db-lab http://127.0.0.1:8888/sse
```

This establishes a Server-Sent Events (SSE) connection between Claude Code and your easy-db-lab MCP server.

## Available MCP Operations

The MCP server provides Claude with direct access to easy-db-lab operations, allowing for:

- Cluster management and provisioning
- Cassandra configuration and deployment
- Performance testing and monitoring
- Log analysis and troubleshooting
- Automated cluster operations

## Benefits of MCP Integration

| Benefit | Description |
|---------|-------------|
| **Direct Control** | Claude can execute easy-db-lab commands directly without manual intervention |
| **Context Awareness** | Claude maintains context about your cluster state and configuration |
| **Automation** | Complex multi-step operations can be automated through Claude |
| **Intelligent Assistance** | Claude can analyze logs, metrics, and provide optimization recommendations |

## Example Workflow

1. Start the MCP server in one terminal:
   ```bash
   easy-db-lab server --port 8888
   ```

2. Add it to Claude Code:
   ```bash
   claude mcp add --transport sse easy-db-lab http://127.0.0.1:8888/sse
   ```

3. Ask Claude to help manage your cluster:
   - "Initialize a new 5-node Cassandra cluster"
   - "Check the status of all nodes"
   - "Collect performance artifacts"
