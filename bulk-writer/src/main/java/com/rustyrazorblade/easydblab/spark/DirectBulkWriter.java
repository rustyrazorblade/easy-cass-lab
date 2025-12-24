package com.rustyrazorblade.easydblab.spark;

import java.util.HashMap;
import java.util.Map;

/**
 * Bulk writer that uses DIRECT transport mode.
 * Data is written directly to Cassandra via Sidecar.
 *
 * This mode is suitable for:
 * - Lower latency writes
 * - Smaller datasets
 * - When S3 is not available or not desired
 *
 * Usage:
 *   spark-submit --class com.rustyrazorblade.easydblab.spark.DirectBulkWriter \
 *     bulk-writer.jar host1,host2,host3 mykeyspace mytable datacenter1 [rowCount] [parallelism] [replicationFactor] [--skip-ddl]
 */
public class DirectBulkWriter extends AbstractBulkWriter {

    public static void main(String[] args) {
        DirectBulkWriter writer = new DirectBulkWriter();
        try {
            writer.run(args);
        } catch (Exception e) {
            System.err.println("Error during bulk write: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    public void run(String[] args) {
        parseArgs(args);
        setupSchema();
        initSpark("DirectBulkWriter");

        try {
            writeData();
        } finally {
            cleanup();
        }
    }

    @Override
    protected Map<String, String> getTransportWriteOptions() {
        Map<String, String> options = new HashMap<>();
        options.put("data_transport", "DIRECT");
        return options;
    }
}
