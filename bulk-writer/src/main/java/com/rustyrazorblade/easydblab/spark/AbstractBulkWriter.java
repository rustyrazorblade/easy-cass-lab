package com.rustyrazorblade.easydblab.spark;

import org.apache.cassandra.spark.bulkwriter.BulkSparkConf;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.RowFactory;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.StructType;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Base class for Cassandra bulk writers using the Analytics library.
 * Provides common configuration and data generation logic.
 */
public abstract class AbstractBulkWriter {

    protected static final String CASSANDRA_DATA_SINK =
        "org.apache.cassandra.spark.sparksql.CassandraDataSink";

    protected SparkSession spark;
    protected JavaSparkContext javaSparkContext;

    // Configuration from command line
    protected String sidecarContactPoints;
    protected String keyspace;
    protected String table;
    protected String localDc;
    protected int rowCount;
    protected int parallelism;
    protected int replicationFactor;
    protected boolean skipDdl;

    /**
     * Schema for bulk writing: id (bigint), course (blob), marks (bigint)
     * Matches the cassandra-analytics example schema.
     */
    protected StructType getSchema() {
        return new StructType()
            .add("id", DataTypes.LongType, false)
            .add("course", DataTypes.BinaryType, false)
            .add("marks", DataTypes.LongType, false);
    }

    /**
     * Generate test dataset matching the schema.
     */
    protected JavaRDD<Row> generateDataset(JavaSparkContext sc, int rowCount, int parallelism) {
        List<Long> seeds = new ArrayList<>();
        for (int i = 0; i < parallelism; i++) {
            seeds.add((long) i);
        }

        int rowsPerPartition = rowCount / parallelism;

        return sc.parallelize(seeds, parallelism).flatMap(seed -> {
            List<Row> rows = new ArrayList<>();
            Random random = new Random(seed);
            long startId = seed * rowsPerPartition;

            for (int i = 0; i < rowsPerPartition; i++) {
                long id = startId + i;
                byte[] courseBytes = new byte[16];
                random.nextBytes(courseBytes);
                ByteBuffer course = ByteBuffer.wrap(courseBytes);
                long marks = random.nextInt(100);
                rows.add(RowFactory.create(id, course.array(), marks));
            }
            return rows.iterator();
        });
    }

    /**
     * Parse command line arguments.
     * Expected: sidecarContactPoints keyspace table localDc [rowCount] [parallelism] [replicationFactor] [--skip-ddl]
     */
    protected void parseArgs(String[] args) {
        if (args.length < 4) {
            System.err.println("Usage: <sidecarContactPoints> <keyspace> <table> <localDc> [rowCount] [parallelism] [replicationFactor]");
            System.err.println("  sidecarContactPoints: Comma-separated list of sidecar hosts (e.g., 'host1,host2,host3')");
            System.err.println("  keyspace: Target Cassandra keyspace");
            System.err.println("  table: Target Cassandra table");
            System.err.println("  localDc: Local datacenter name");
            System.err.println("  rowCount: Number of rows to write (default: 1000000)");
            System.err.println("  parallelism: Number of partitions (default: 10)");
            System.err.println("  replicationFactor: Keyspace replication factor (default: 3)");
            System.exit(1);
        }

        // Required positional arguments
        sidecarContactPoints = args[0];
        keyspace = args[1];
        table = args[2];
        localDc = args[3];

        // Defaults for optional arguments
        rowCount = 1000000;
        parallelism = 10;
        replicationFactor = 3;
        skipDdl = false;

        // Parse optional arguments, handling --skip-ddl flag
        int positionalIndex = 4;
        for (int i = 4; i < args.length; i++) {
            if ("--skip-ddl".equals(args[i])) {
                skipDdl = true;
            } else {
                // Positional optional arguments
                switch (positionalIndex) {
                    case 4:
                        rowCount = Integer.parseInt(args[i]);
                        break;
                    case 5:
                        parallelism = Integer.parseInt(args[i]);
                        break;
                    case 6:
                        replicationFactor = Integer.parseInt(args[i]);
                        break;
                    default:
                        System.err.println("Warning: Ignoring extra argument: " + args[i]);
                }
                positionalIndex++;
            }
        }
    }

    /**
     * Initialize Spark session with the given app name.
     * Configures JDK11 options required for Cassandra SSTable generation.
     */
    protected void initSpark(String appName) {
        SparkConf conf = new SparkConf()
            .setAppName(appName)
            .set("spark.serializer", "org.apache.spark.serializer.KryoSerializer");

        // Setup JDK11 options and Kryo registrator required for SSTable generation
        BulkSparkConf.setupSparkConf(conf, true);

        spark = SparkSession.builder()
            .config(conf)
            .getOrCreate();

        javaSparkContext = new JavaSparkContext(spark.sparkContext());
    }

    /**
     * Common write options for both transport modes.
     */
    protected Map<String, String> getBaseWriteOptions() {
        Map<String, String> options = new HashMap<>();
        options.put("sidecar_contact_points", sidecarContactPoints);
        options.put("keyspace", keyspace);
        options.put("table", table);
        options.put("local_dc", localDc);
        options.put("bulk_writer_cl", "LOCAL_QUORUM");
        options.put("number_splits", "-1"); // Auto-calculate
        return options;
    }

    /**
     * Get transport-specific write options. Subclasses implement this.
     */
    protected abstract Map<String, String> getTransportWriteOptions();

    /**
     * Write data to Cassandra using the bulk writer.
     */
    protected void writeData() {
        System.out.println("Generating " + rowCount + " rows with parallelism " + parallelism);

        JavaRDD<Row> rows = generateDataset(javaSparkContext, rowCount, parallelism);
        Dataset<Row> df = spark.createDataFrame(rows, getSchema());

        Map<String, String> writeOptions = getBaseWriteOptions();
        writeOptions.putAll(getTransportWriteOptions());

        System.out.println("Writing to " + keyspace + "." + table + " via " +
            writeOptions.get("data_transport") + " transport");

        df.write()
            .format(CASSANDRA_DATA_SINK)
            .options(writeOptions)
            .mode("append")
            .save();

        System.out.println("Successfully wrote " + rowCount + " rows");
    }

    /**
     * Set up the schema (keyspace and table) before writing data.
     * Uses CqlSetup to create keyspace with NetworkTopologyStrategy and table with fixed schema.
     * Can be skipped with --skip-ddl flag.
     */
    protected void setupSchema() {
        if (skipDdl) {
            System.out.println("Skipping DDL creation (--skip-ddl specified)");
            return;
        }
        try (CqlSetup cqlSetup = new CqlSetup(sidecarContactPoints, localDc)) {
            cqlSetup.setupSchema(keyspace, table, replicationFactor);
        }
    }

    /**
     * Clean up Spark resources.
     */
    protected void cleanup() {
        if (spark != null) {
            spark.stop();
        }
    }
}
