- This is a command line tool.  The user interacts by reading the output.  Do not suggest replacing print statements with logging, because it breaks the UX.

## Development Rules

- All tests should pass before committing.
- Always add tests to new non-trivial code.
- If this document needs to be updated in order to provide more context for future work, do it.
- Do not use remote docker-compose commands, use docker compose, the subcommand version.
- Check if the codebase already has a way of accomplishing something before writing new code.  For example, there's already Docker logic.
- ABSOLUTE RULE: Never try to commit without explicit instruction to do so.
- activate kotlin and java for context7
- activate the serena MCP server
- ABSOLUTE RULE: NEVER attribute commit messages to Claude.  

## Testing Guidelines

### Test Base Class and Dependency Injection
All tests should extend `BaseKoinTest` to get automatic Koin dependency injection setup and teardown. This ensures:
- Critical services (like AWS) are always mocked to prevent real API calls
- Consistent test configuration across the codebase
- Automatic lifecycle management (no manual `startKoin`/`stopKoin` needed)

#### Basic Test Structure
```kotlin
class MyTest : BaseKoinTest() {
    // Your test methods here
}
```

#### Tests Requiring Additional Modules
If your test needs additional mocked services beyond the core modules:

```kotlin
class MyTestWithExtraModules : BaseKoinTest() {
    override fun additionalTestModules(): List<Module> =
        listOf(
            // Add your additional test-specific modules here
            module {
                single { mock<MyCustomService>() }
            }
        )
    
    @Test
    fun `test with additional services`() {
        val customService: MyCustomService by inject()
        // Test code here
    }
}
```

Note: SSH, AWS, and OutputHandler modules are already included in core modules and don't need to be added.

#### Creating Custom Test Mocks
For test-specific mocks:

```kotlin
class MyTestWithCustomMocks : BaseKoinTest() {
    private val mockService = mock<MyService>()
    
    override fun additionalTestModules(): List<Module> =
        listOf(
            module {
                single { mockService }
            }
        )
    
    @Test
    fun `test with custom mock`() {
        whenever(mockService.doSomething()).thenReturn("mocked value")
        
        val service: MyService by inject()
        assertThat(service.doSomething()).isEqualTo("mocked value")
    }
}
```

### Core Test Modules
The `BaseKoinTest` automatically provides these core modules that should ALWAYS be mocked:

1. **AWS Module** (`testAWSModule`):
   - Provides mocked `AWS` service with mocked `Clients`
   - Prevents real AWS API calls and charges
   - Includes mock User configuration

2. **Output Module** (`testOutputModule`):
   - Provides `BufferedOutputHandler` for capturing output in tests
   - Prevents console output during test runs

3. **SSH Module** (`testSSHModule`):
   - Provides mocked SSH configuration and connections
   - Prevents real SSH connections to remote hosts
   - Includes mock `RemoteOperationsService` for simulating remote operations

### Mocking AWS Services
When testing code that interacts with AWS services, the AWS module is automatically mocked by `BaseKoinTest`. 
For tests that need to verify specific AWS behavior:

```kotlin
class AWSTest : BaseKoinTest() {
    @Test
    fun `test AWS operation`() {
        // AWS is automatically injected with mocked clients
        val aws: AWS by inject()
        val clients: Clients by inject()
        
        // The AWS instance is real but uses mocked clients
        // This ensures service logic is tested without real API calls
        
        // For lower-level testing with custom mock responses:
        val mockIamClient = mock<IamClient>()
        val mockClients = mock<Clients>()
        whenever(mockClients.iam).thenReturn(mockIamClient)
        whenever(mockIamClient.createRole(any())).thenReturn(mockResponse)
        
        val customAws = AWS(mockClients)
        // Test with custom AWS instance
    }
}
```

The project uses `mockito-kotlin` for mocking. See `AWSTest.kt` for a complete example of testing AWS services with mocked clients.

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
- Don't use wildcard imports.
- Always use assertj style assertions, not the raw junit ones.
- Constants and magic numbers should be stored in com.rustyrazorblade.easycasslab.Constants
- When migrating code, it is not necessary to maintain backwards compability.