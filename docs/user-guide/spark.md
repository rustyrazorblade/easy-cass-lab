# Spark

easy-db-lab supports provisioning Apache Spark clusters via AWS EMR for analytics workloads.

## Enabling Spark

Spark is enabled during cluster initialization with the `--spark.enable` flag:

```bash
easy-db-lab init --spark.enable
```

### Spark Configuration Options

| Option | Description | Default |
|--------|-------------|---------|
| `--spark.enable` | Enable Spark EMR cluster | false |
| `--spark.master.instance.type` | Master node instance type | m5.xlarge |
| `--spark.worker.instance.type` | Worker node instance type | m5.xlarge |
| `--spark.worker.instance.count` | Number of worker nodes | 3 |

### Example with Custom Configuration

```bash
easy-db-lab init \
  --spark.enable \
  --spark.master.instance.type m5.2xlarge \
  --spark.worker.instance.type m5.4xlarge \
  --spark.worker.instance.count 5
```

## Submitting Spark Jobs

Submit JAR-based Spark applications to your EMR cluster:

```bash
easy-db-lab spark submit \
  --jar /path/to/your-app.jar \
  --main-class com.example.YourMainClass \
  --args "arg1 arg2"
```

### Submit Options

| Option | Description | Required |
|--------|-------------|----------|
| `--jar` | Path to JAR file (local or s3://) | Yes |
| `--main-class` | Main class to execute | Yes |
| `--args` | Arguments for the Spark application | No |
| `--wait` | Wait for job completion | No |
| `--name` | Job name (defaults to main class) | No |

### Using S3 for JARs

You can upload your JAR to S3 and reference it directly:

```bash
# Upload JAR to cluster S3 bucket
aws s3 cp your-app.jar s3://your-bucket/spark/your-app.jar

# Submit using S3 path
easy-db-lab spark submit \
  --jar s3://your-bucket/spark/your-app.jar \
  --main-class com.example.YourMainClass \
  --wait
```

## Checking Job Status

### View Recent Jobs

List recent Spark jobs on the cluster:

```bash
easy-db-lab spark jobs
```

Options:

- `--limit` - Maximum number of jobs to display (default: 10)

### Check Specific Job Status

```bash
easy-db-lab spark status --step-id <step-id>
```

Without `--step-id`, shows the status of the most recent job.

Options:

- `--step-id` - EMR step ID to check
- `--logs` - Download step logs (stdout, stderr)

## Retrieving Logs

Download logs for a Spark job:

```bash
easy-db-lab spark logs --step-id <step-id>
```

Logs are automatically decompressed and include:

- `stdout.gz` - Standard output
- `stderr.gz` - Standard error
- `controller.gz` - EMR controller logs

## Architecture

When Spark is enabled, easy-db-lab provisions:

- **EMR Cluster**: Managed Spark cluster with master and worker nodes
- **S3 Integration**: Logs stored at `s3://<bucket>/spark/emr-logs/`
- **IAM Roles**: Service and job flow roles for EMR operations

### Timeouts and Polling

- **Job Polling Interval**: 5 seconds
- **Maximum Wait Time**: 4 hours
- **Cluster Creation Timeout**: 30 minutes

## Spark with Cassandra

A common use case is running Spark jobs that read from or write to Cassandra. Use the [Spark Cassandra Connector](https://github.com/datastax/spark-cassandra-connector):

```scala
import com.datastax.spark.connector._

val df = spark.read
  .format("org.apache.spark.sql.cassandra")
  .options(Map("table" -> "my_table", "keyspace" -> "my_keyspace"))
  .load()
```

Ensure your JAR includes the Spark Cassandra Connector dependency and configure the Cassandra host in your Spark application.
