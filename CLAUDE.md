- This is a command line tool.  The user interacts by reading the output.  Do not suggest replacing print statements with logging, because it breaks the UX.

## Development Rules

- All tests should pass before committing.
- Always add tests to new code.
- If this document needs to be updated in order to provide more context for future work, do it.
- Do not use remote docker-compose commands, use docker compose, the subcommand version.
- Check if the codebase already has a way of accomplishing something before writing new code.  For example, there's already Docker logic.
- ABSOLUTE RULE: Never try to commit without explicit instruction to do so.
- activate easy-cass-lab (son)

## Testing Guidelines

### Mocking AWS Services
When writing tests that would normally interact with AWS services (IAM, EC2, EMR, etc.), always use mocked clients to avoid:
- Making actual AWS API calls during tests
- Requiring AWS credentials for test execution
- Incurring AWS charges from test runs
- Test failures due to network issues or AWS service availability

Example pattern for mocking AWS services:

```kotlin
// Create mock clients
val mockIamClient = mock<IamClient>()
val mockClients = mock<Clients>()
whenever(mockClients.iam).thenReturn(mockIamClient)

// Setup mock responses
whenever(mockIamClient.createRole(any())).thenReturn(mockResponse)

// Test with mocked clients
val aws = AWS(mockClients)
```

The project uses `mockito-kotlin` for mocking. See `AWSTest.kt` for a complete example.

## Hostname and IP Management

### Overview
The codebase manages hostnames and IP addresses through the `TFState` class, which parses Terraform state to extract host information for provisioned instances.

### Key Components

#### 1. Host Data Class (`configuration/Host.kt`)
Represents a single host with:
- `public`: Public IP address
- `private`: Private/internal IP address  
- `alias`: Host alias (e.g., "cassandra0", "stress0", "control0")
- `availabilityZone`: AWS availability zone

#### 2. ServerType Enum (`configuration/ServerType.kt`)
Defines three server types:
- `Cassandra`: Cassandra database nodes
- `Stress`: Stress testing nodes
- `Control`: Control/monitoring nodes

#### 3. TFState Class (`configuration/TFState.kt`)
Parses Terraform state and provides methods to retrieve host information:
- `getHosts(serverType: ServerType)`: Returns list of hosts for a given server type
- `withHosts(serverType: ServerType, hostFilter: Hosts, action: (Host) -> Unit)`: Executes action on filtered hosts

#### 4. Commands (`commands/*`)

JCommander subcommands.  Most run then exit.  There are two exceptions:

- Repl: Starts a REPL to reduce typing
- McpCommand: Starts an MCP server for AI Agents.


### Common Patterns

#### Getting Host IPs
```kotlin
// Get the internal IP of the first Cassandra node
val cassandraHost = context.tfstate.getHosts(ServerType.Cassandra).first().private

// Get all Cassandra hosts
val allCassandraHosts = context.tfstate.getHosts(ServerType.Cassandra)

// Iterate over control nodes
context.tfstate.withHosts(ServerType.Control, hosts) { host ->
    // host.public - public IP
    // host.private - internal IP
    // host.alias - hostname alias
}
```

#### Docker Compose Templating
The `docker-compose.yaml` file is:
1. Initially extracted from resources with placeholder values (e.g., `cassandra0`)
2. Updated in `Up.kt::uploadDockerComposeToControlNodes()` to replace placeholders with actual IPs
3. Uploaded to control nodes with real IP addresses

This ensures services on control nodes can connect to Cassandra nodes using their internal IPs.


## Open Telemetry

Cassandra and control nodes are set up with OpenTelemetry.

Local OTel nodes are forwarding metrics to the control node.

## User Manual

The user manual is located in manual/index.adoc.  