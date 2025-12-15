# End-to-End Testing

easy-db-lab includes a comprehensive end-to-end test suite that validates the entire workflow from provisioning to teardown.

## Running the Test

The end-to-end test is located at `bin/end-to-end-test`:

```bash
./bin/end-to-end-test --cassandra
```

## Command-Line Options

### Feature Flags

| Flag | Description |
|------|-------------|
| `--cassandra` | Enable Cassandra-specific tests |
| `--spark` | Enable Spark EMR provisioning and tests |
| `--clickhouse` | Enable ClickHouse deployment and tests |
| `--opensearch` | Enable OpenSearch deployment and tests |
| `--all` | Enable all optional features |
| `--ebs` | Enable EBS volumes (gp3, 256GB) |
| `--build` | Build Packer images (default: skip) |

### Testing and Inspection

| Flag | Description |
|------|-------------|
| `--list-steps`, `-l` | List all test steps without running |
| `--break <steps>` | Set breakpoints at specific steps (comma-separated) |
| `--wait` | Run all steps except teardown, then wait for confirmation |

## Examples

```bash
# List all available test steps
./bin/end-to-end-test --list-steps

# Run full test with all features
./bin/end-to-end-test --all

# Run with Cassandra and pause before teardown
./bin/end-to-end-test --cassandra --wait

# Run with breakpoints at steps 5 and 15
./bin/end-to-end-test --cassandra --break 5,15

# Build custom AMI images and run test
./bin/end-to-end-test --build --cassandra
```

## Test Steps

The test executes approximately 27 steps covering:

### Infrastructure

1. Build project
2. Check version command
3. Build packer images (optional)
4. Set IAM policies
5. Initialize cluster
6. Setup kubectl
7. Wait for K3s ready
8. Verify K3s cluster

### Registry and Storage

9. Test registry push/pull
10. List hosts
11. Verify S3 backup

### Cassandra

12. Setup Cassandra
13. Verify Cassandra backup
14. Verify restore
15. Cassandra start/stop cycle
16. Test SSH and nodetool
17. Check Sidecar
18. Test exec command
19. Run stress test
20. Run stress K8s test

### Optional Services

21. Submit Spark job (if `--spark`)
22. Check Spark status (if `--spark`)
23. Start ClickHouse (if `--clickhouse`)
24. Test ClickHouse (if `--clickhouse`)
25. Stop ClickHouse (if `--clickhouse`)
26. Start OpenSearch (if `--opensearch`)
27. Test OpenSearch (if `--opensearch`)
28. Stop OpenSearch (if `--opensearch`)

### Observability and Cleanup

29. Test observability stack
30. Teardown cluster

## Error Handling

When a test step fails, an interactive menu appears:

1. **Retry from failed step** - Resume from the point of failure
2. **Start a shell session** - Opens a shell with:
   - `easy-db-lab` commands available
   - `rebuild` - Rebuild just the project
   - `rerun` - Rebuild and resume from failed step
3. **Tear down environment** - Run `easy-db-lab down --yes`
4. **Exit** - Exit the script

## AWS Requirements

The test requires:

- AWS profile with sufficient permissions
- VPC and subnet configuration
- S3 bucket for backups and logs

### Default Configuration

- Instance count: 3 nodes
- Instance type: c5.2xlarge
- Cassandra version: 5.0 (when enabled)
- Spark workers: 2 (when enabled)

## CI Integration

The end-to-end test is designed to run in CI environments:

- Supports non-interactive mode
- Returns appropriate exit codes
- Provides detailed logging
- Cleans up resources on failure
