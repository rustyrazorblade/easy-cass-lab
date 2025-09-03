# Dependency Injection Implementation Plan

This document outlines candidates for Koin dependency injection conversion in the easy-cass-lab codebase.

## Analysis Overview

Based on analysis of the current codebase, the following classes are excellent candidates for Koin dependency injection conversion. The codebase already has a solid foundation with OutputHandler, RemoteOperationsService, and Docker classes converted to use KoinComponent.

## High Priority - Service & Provider Classes

### 1. AWS & Cloud Provider Classes
- **`Clients`** (`providers/aws/Clients.kt`) - Constructor takes `User`, manages AWS clients
- **`AWS`** (`providers/AWS.kt`) - Constructor takes `Clients`, core AWS service interface  
- **`EC2`** (`providers/aws/EC2.kt`) - Constructor takes credentials & region parameters
- **`AWSConfiguration`** (`providers/aws/terraform/AWSConfiguration.kt`) - Complex constructor with many parameters

### 2. Infrastructure & Configuration Classes
- **`TFState`** (`configuration/TFState.kt`) - Constructor takes `Context` + `InputStream`
- **`Terraform`** (`containers/Terraform.kt`) - Already injecting Docker, good candidate
- **`Packer`** (`containers/Packer.kt`) - Already injecting Docker + OutputHandler

### 3. Command Classes (Build Commands)
- **`BuildImage`**, **`BuildCassandraImage`**, **`BuildBaseImage`** - All take Context, could inject Docker
- **`Repl`** - Takes Context, could inject CommandLineParser or other services

## Medium Priority - Utility Classes

### 4. Configuration & Parser Classes
- **`CommandLineParser`** - Already has `dockerClientProvider: DockerClientProvider by inject()`
- **`CassandraYaml`** - Constructor takes `JsonNode`, could inject JSON parsing utilities
- **`User`** class - Could inject validation services

### 5. Domain Model Classes with Dependencies
Some data classes have constructor dependencies that could be injected:
- **`VolumeMapping`** (has companion object with logger)
- Configuration classes that create complex objects

## Lower Priority - Consider If Beneficial

### 6. Command Classes Already Using BaseCommand
Most commands extending `BaseCommand` already have `remoteOps` and `outputHandler` injected, but some individual commands might benefit from additional injections.

## Recommended Implementation Order

### Phase 1: Core Infrastructure
1. **`Clients`** → Create AWS module for AWS-related dependencies
2. **`AWS`** → Depends on Clients
3. **`TFState`** → Core terraform state management

### Phase 2: Container & Build System
4. **`Terraform`** → Container wrapper for terraform operations
5. **Build command classes** → Inject Docker and other build dependencies

### Phase 3: Configuration & Utilities
6. **`AWSConfiguration`** → Complex configuration class with many dependencies
7. **Parser and utility classes** as needed

## Benefits of Converting These Classes

✅ **Testability** - Easy mocking of AWS services, Docker clients  
✅ **Configuration** - Centralized dependency management  
✅ **Flexibility** - Easy swapping of implementations (mock vs real AWS)  
✅ **Consistency** - Aligns with existing KoinComponent pattern  
✅ **Maintenance** - Reduces constructor complexity

## Current DI Status

### Already Converted to KoinComponent
- `Docker` - Core Docker operations
- `ContainerExecutor`, `ContainerIOManager`, `ContainerStateMonitor` - Docker container management
- `Dashboards` - Dashboard management
- `DefaultSSHConnectionProvider`, `DefaultRemoteOperationsService` - SSH operations
- `SSHClient` - SSH client wrapper
- Most command classes via `BaseCommand` - Command execution with injected RemoteOperationsService and OutputHandler
- `McpToolRegistry`, `McpServer` - MCP server functionality

### Key Patterns Established
- `KoinComponent` interface implementation
- `by inject()` property delegation for dependencies
- Factory methods updated to remove constructor parameters
- Test classes use `KoinTestHelper` for consistent Koin context setup

## Next Steps

The **AWS/Cloud provider classes** are the highest value targets since they're used throughout the application and would greatly benefit from centralized configuration and easy mocking for tests.

Implementation should follow the established patterns:
1. Convert class to implement `KoinComponent`
2. Change constructor dependencies to `private val dependency: Type by inject()`
3. Update factory methods and callers to remove dependency parameters
4. Update Koin modules to provide the dependencies
5. Update tests to use proper Koin context setup