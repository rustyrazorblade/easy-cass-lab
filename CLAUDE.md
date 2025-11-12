- This is a command line tool.  The user interacts by reading the output.  Do not suggest replacing print statements with logging, because it breaks the UX.
- Commands should use `outputHandler.handleMessage()` for user-facing output, not logging frameworks.
- Do not add logging frameworks to command classes unless there is a specific internal debugging need separate from user output.
- When logging is needed, use: `import io.github.oshai.kotlinlogging.KotlinLogging` and create a logger with `private val log = KotlinLogging.logger {}`

## Development Setup

### Pre-commit Hook Installation

Install the ktlint pre-commit hook to automatically check code style before commits:

```bash
./gradlew addKtlintCheckGitPreCommitHook
```

**Important**: Pre-commit hooks are stored in `.git/hooks/` which is local-only and not tracked by Git. Each developer must run this command individually to install the hook on their machine.

The hook automatically runs `ktlintCheck` on staged Kotlin files before each commit, preventing style violations from being committed.

### Configuration Cache

The project uses Gradle configuration cache for faster builds, enabled via `gradle.properties`:
- `org.gradle.configuration-cache=true` - Enables configuration caching
- `org.gradle.caching=true` - Enables build caching

**When to clear the cache**:
- After modifying `.editorconfig` or ktlint rules
- After changing Gradle plugins or build scripts
- When encountering unexpected build behavior

```bash
# Clear configuration cache
rm -rf .gradle/configuration-cache

# Or clean everything
./gradlew clean
```

**Why this matters**: If you modify `.editorconfig` (which configures ktlint rules), the configuration cache may prevent ktlint from seeing the new rules. This can cause local builds to pass while CI fails with style violations.

### Local Validation

Before pushing code, verify it passes all checks:

```bash
# Run all checks (matches CI)
./gradlew check

# Run only ktlint check (verify style compliance)
./gradlew ktlintCheck

# Auto-fix ktlint violations (when possible)
./gradlew ktlintFormat
```

**Note**: `ktlintFormat` auto-fixes many violations but can't fix all issues (e.g., line length). Always run `ktlintCheck` after formatting to catch remaining issues.

### Packer Script Testing

Test packer provisioning scripts locally using Docker (no AWS required):

```bash
# Test base provisioning scripts
./gradlew testPackerBase

# Test Cassandra provisioning scripts
./gradlew testPackerCassandra

# Run all packer tests
./gradlew testPacker

# Test a specific script
./gradlew testPackerScript -Pscript=cassandra/install/install_cassandra_easy_stress.sh
```

For more details, see [packer/README.md](packer/README.md) and [packer/TESTING.md](packer/TESTING.md).

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
- Do not use wildcard imports.
- When making changes, use the detekt plugin to determine if there are any code quality regressions.
- Always ensure files end with a newline
- Tests should extend BaseKoinTest to use Koin DI
- Use resilience4j for retry logic instead of custom retry loops

### Retry Logic with Resilience4j

The project uses resilience4j for all retry operations. Never implement custom retry loops.

Example:
```kotlin
import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryConfig
import java.time.Duration

// Configure retry behavior
val retryConfig = RetryConfig
    .custom<ResultType>()
    .maxAttempts(3)
    .retryExceptions(IOException::class.java)
    .intervalFunction { attemptCount ->
        // Exponential backoff: 2s, 4s, 8s
        2000L * (1L shl (attemptCount - 1))
    }
    .build()

val retry = Retry.of("operation-name", retryConfig)

// Use the retry
val result = Retry.decorateSupplier(retry) {
    // Operation that may fail and need retry
}.get()
```

See `DefaultRemoteOperationsService` for production examples.

## Testing Guidelines

For comprehensive testing guidelines, including custom assertions and Domain-Driven Design patterns, see [docs/TESTING.md](docs/TESTING.md).

### Key Principles
- Extend `BaseKoinTest` for all tests (provides automatic DI and mocked services)
- Use AssertJ assertions, not JUnit assertions  
- Create custom assertions for domain classes to enable Domain-Driven Design
- AWS, SSH, and OutputHandler are automatically mocked by BaseKoinTest

### Quick Example: Mocking AWS Services
When writing tests that interact with AWS services, always use mocked clients to prevent real API calls:

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

The project uses `mockito-kotlin` for mocking. See `AWSTest.kt` for complete examples.

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
- Fail fast is usually preferred.