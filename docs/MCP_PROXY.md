# MCP Proxy Design Documentation

> **Note**: This document should be kept up to date as the MCP Proxy implementation progresses. Any design changes, learnings, or architectural decisions should be reflected here.

## Overview

The MCP (Model Context Protocol) Proxy system enables the local easy-cass-lab MCP server to discover, register, and proxy tools from remote MCP servers running on control nodes. This creates a unified interface where AI agents can access both local and remote tools transparently.

## Architecture

### System Components

```
┌──────────────────┐
│   AI Agent       │
│  (Claude, etc)   │
└────────┬─────────┘
         │ SSE
         ▼
┌──────────────────┐
│  Local MCP       │
│   Server         │
│  (port 8888)     │
├──────────────────┤
│ RemoteMcp        │
│ ToolRegistry     │
├──────────────────┤
│ RemoteMcpProxy   │
└────┬─────────────┘
     │ SSE
     ▼
┌──────────────────┐     ┌──────────────────┐
│ Control Node 0   │     │ Control Node N   │
│  MCP Server      │ ... │  MCP Server      │
│  (port 8000)     │     │  (port 8000)     │
└──────────────────┘     └──────────────────┘
```

### Key Classes

1. **Context** - Enhanced with `isMcpMode: Boolean` to enable remote MCP features
2. **DockerComposeParser** - Parses docker-compose.yaml to extract MCP service configuration
3. **RemoteMcpDiscovery** - Discovers available remote MCP servers on control nodes
4. **McpToolRegistry** - Enhanced to include remote tools when in MCP mode (no separate class needed)
5. **RemoteMcpProxy** - (Future) Will handle actual SSE connections and request forwarding to remote servers

## Current State

### Remote MCP Server Configuration
- **Service**: `easy-cass-mcp` in docker-compose.yaml
- **Port**: 8000 (hardcoded in healthcheck)
- **Network**: Host mode
- **Endpoint**: `http://<control_node_ip>:8000/sse`

### Local MCP Server
- **Port**: 8888 (configurable via --port)
- **Default**: Constants.Network.DEFAULT_MCP_PORT

## Design Decisions

### 1. Port Discovery
- **Source of Truth**: Local `control/docker-compose.yaml`
- **Method**: Parse healthcheck configuration for port
- **Future**: Support explicit port configuration if added

### 2. Tool Naming Convention
- **Format**: `<node_name>.<tool_name>`
- **Example**: `control0.init`, `control0.up`
- **Purpose**: Avoid naming conflicts, clear origin identification

### 3. Discovery Timing
- **When**: On local MCP server startup only
- **Storage**: In-memory only (no persistence)
- **Benefit**: Always reflects current cluster state

### 4. Error Handling
- **Graceful Degradation**: System works with only local tools if remotes unavailable
- **Partial Failures**: Individual remote server failures don't affect others
- **Connection Retries**: Automatic retry with exponential backoff

### 5. No Tool Filtering
- All discovered remote tools are exposed
- AI agents can access any tool from any node

## Implementation Status

### Completed Phases

#### Phase 1: Context Setup ✅
**Goal**: Add MCP mode awareness to Context

**Changes**:
```kotlin
data class Context(val easycasslabUserDirectory: File) : KoinComponent {
    // ... existing fields ...
    var isMcpMode: Boolean = false  // New field
}
```

**Initialization**:
- Set to `true` in `McpCommand.execute()`
- Default `false` for all other commands

**Tests**:
- Verify mode is set correctly for McpCommand
- Verify mode is false for other commands

#### Phase 2: Docker Compose Parser ✅
**Goal**: Extract MCP service configuration

**Class**: `DockerComposeParser`
```kotlin
class DockerComposeParser {
    data class McpServiceInfo(
        val serviceName: String,
        val port: Int,
        val healthcheckUrl: String?
    )

    fun parseMcpService(file: File): McpServiceInfo?
}
```

**Implementation**:
- Parse YAML structure
- Find `easy-cass-mcp` service
- Extract port from healthcheck
- Handle missing file/service gracefully

**Tests**:
- Parse standard docker-compose format
- Handle missing MCP service
- Handle malformed YAML

#### Phase 3: Remote Discovery ✅
**Goal**: Discover remote MCP servers

**Class**: `RemoteMcpDiscovery`
```kotlin
class RemoteMcpDiscovery(
    private val context: Context,
    private val tfStateProvider: TFStateProvider
) {
    data class RemoteServer(
        val nodeName: String,
        val host: String,
        val port: Int,
        val endpoint: String
    )

    fun discoverRemoteServers(): List<RemoteServer>
}
```

**Implementation**:
1. Parse docker-compose.yaml using DockerComposeParser
2. Get control node IPs from TFState
3. Build endpoint URLs
4. Verify connectivity with health checks
5. Return list of available servers

**Tests**:
- Mock TFState responses
- Mock health check responses
- Handle unreachable servers

#### Phase 4: Tool Registry Integration ✅
**Goal**: Extend registry to include remote tools

**Approach**: Enhanced `McpToolRegistry` directly (no separate class needed)

**Implementation**:
- Added remote tool discovery to existing `McpToolRegistry`
- Remote tools are prefixed with `remote_<node>_`
- Tools are discovered dynamically when `isMcpMode` is true
- Placeholder remote tool execution (actual SSE communication pending)

**Key Changes to McpToolRegistry**:
```kotlin
// Added fields for remote support
private var remoteMcpDiscovery: RemoteMcpDiscovery? = null
private var remoteServers: List<RemoteMcpDiscovery.RemoteServer> = emptyList()
private val remoteToolMap = mutableMapOf<String, RemoteMcpDiscovery.RemoteServer>()

// Enhanced getTools() to include remote tools
override fun getTools(): List<ToolInfo> {
    val localTools = // ... existing local tools

    if (!context.isMcpMode) return localTools

    // Discover and add remote tools
    remoteServers = remoteMcpDiscovery!!.discoverRemoteServers()
    // ... create and return combined tool list
}

// Enhanced executeTool() to handle remote execution
override fun executeTool(name: String, arguments: JsonObject?): ToolResult {
    val remoteServer = remoteToolMap[name]
    if (remoteServer != null) {
        return executeRemoteTool(name, arguments, remoteServer)
    }
    // ... existing local execution
}
```

#### Phase 5: Start Command Integration ✅
**Goal**: Discover remote servers when Docker containers start

**Implementation**: Added PostExecute hook to Start command

```kotlin
@PostExecute
fun discoverRemoteMcpServers() {
    if (!context.isMcpMode) return

    val discovery = RemoteMcpDiscovery(context)
    val remoteServers = discovery.discoverRemoteServers()

    log.info { "Discovered ${remoteServers.size} remote MCP servers" }
}
```

**Result**: Remote MCP servers are discovered automatically when:
1. The Start command completes
2. The context is in MCP mode

### Future Work

#### Phase 6: Remote Proxy Implementation
**Goal**: Actual SSE/HTTP communication to remote servers

**Future Class**: `RemoteMcpProxy`
```kotlin
class RemoteMcpProxy {
    private val sseClients = mutableMapOf<String, SseClient>()

    fun connectToServer(server: RemoteServer): Boolean
    fun forwardToolRequest(toolName: String, args: JsonObject?): ToolResult
    fun disconnectAll()
}
```

**Implementation**:
- Use Ktor SSE client
- Maintain connection pool
- Parse tool name for routing
- Stream responses back

**Tests**:
- Mock remote MCP servers
- Test request forwarding
- Test connection failures
- Test response streaming

**When Implemented**:
- Will handle actual SSE/HTTP connections
- Will fetch real tool lists from remote servers
- Will stream tool execution responses back to AI agents

## Testing Strategy

### Unit Tests
- Each class tested in isolation
- Mock external dependencies
- Focus on error cases

### Integration Tests
- Test component interactions
- Mock MCP servers using Ktor
- Verify SSE communication

### Test Scenarios
1. No remote servers available
2. Some remote servers unavailable
3. Remote server becomes unavailable during operation
4. Malformed responses from remote servers
5. Tool name conflicts
6. Large number of remote tools

## API Contracts

### Remote MCP Server Endpoints
- **Tools List**: `GET /tools/list`
- **Tool Execution**: `POST /tools/call`
- **Health Check**: `GET /health`

### Tool List Response
```json
{
  "tools": [
    {
      "name": "tool_name",
      "description": "Tool description",
      "inputSchema": { ... }
    }
  ]
}
```

### Tool Execution Request
```json
{
  "name": "tool_name",
  "arguments": { ... }
}
```

## Future Enhancements

### Near Term
- [ ] Support port configuration via environment variables
- [ ] Add connection health monitoring
- [ ] Implement connection pooling with size limits

### Long Term
- [ ] Authentication between MCP servers
- [ ] Tool permission management
- [ ] Metrics and observability
- [ ] Tool response caching
- [ ] Load balancing across multiple control nodes

## Development Guidelines

### Adding New Features
1. Update this document first
2. Write tests following TDD
3. Implement incrementally
4. Ensure system remains functional after each change

### Code Style
- Use Kotlin idioms
- Follow existing patterns in the codebase
- Add KDoc comments for public APIs
- Use meaningful variable names

### Error Handling
- Never crash the local MCP server due to remote issues
- Log errors with appropriate levels
- Provide useful error messages to AI agents

## Monitoring and Debugging

### Logging
- Use KotlinLogging consistently
- Log levels:
  - ERROR: Connection failures, tool execution failures
  - WARN: Retries, degraded functionality
  - INFO: Successful connections, tool registrations
  - DEBUG: Request/response details

### Health Checks
- Local MCP server health includes remote server status
- Expose endpoint for checking remote connections
- Include in status responses to AI agents

## Appendix

### Sample Docker Compose (Current)
```yaml
services:
  easy-cass-mcp:
    image: rustyrazorblade/easy-cass-mcp:latest
    container_name: easy-cass-mcp
    environment:
      - CASSANDRA_HOST=${CASSANDRA_HOST}
      - CASSANDRA_DATACENTER=${CASSANDRA_DATACENTER}
    restart: unless-stopped
    network_mode: host
    healthcheck:
      test: ["CMD", "bash", "-c", "exec 6<> /dev/tcp/localhost/8000"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 40s
```

### Constants
- Local MCP Port: 8888 (DEFAULT_MCP_PORT)
- Remote MCP Port: 8000 (from docker-compose)
- SSE Endpoint: `/sse`
- Health Endpoint: `/health`