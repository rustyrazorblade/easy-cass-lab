package com.rustyrazorblade.easydblab.spark;

import java.util.HashMap;
import java.util.Map;

/**
 * Bulk writer that uses S3_COMPAT transport mode.
 * Data is written to S3-compatible storage, then imported via Sidecar.
 *
 * This mode is suitable for:
 * - Large datasets
 * - When direct connection to all Cassandra nodes is not possible
 * - Leveraging S3 for intermediate storage
 *
 * Additional environment variables or system properties required:
 * - S3_BUCKET: Target S3 bucket name (required)
 * - S3_ENDPOINT: S3 endpoint URL (optional, defaults to AWS S3)
 *
 * Usage:
 *   S3_BUCKET=my-bucket spark-submit \
 *     --class com.rustyrazorblade.easydblab.spark.S3BulkWriter \
 *     bulk-writer.jar host1,host2,host3 mykeyspace mytable datacenter1 [rowCount] [parallelism] [replicationFactor] [--skip-ddl]
 */
public class S3BulkWriter extends AbstractBulkWriter {

    private String s3Endpoint;
    private String s3Bucket;

    public static void main(String[] args) {
        S3BulkWriter writer = new S3BulkWriter();
        try {
            writer.run(args);
        } catch (Exception e) {
            System.err.println("Error during S3 bulk write: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    public void run(String[] args) {
        parseArgs(args);
        parseS3Config();
        setupSchema();
        initSpark("S3BulkWriter");

        try {
            writeData();
        } finally {
            cleanup();
        }
    }

    /**
     * Parse S3-specific configuration from environment or system properties.
     */
    private void parseS3Config() {
        s3Endpoint = System.getenv("S3_ENDPOINT");
        if (s3Endpoint == null) {
            s3Endpoint = System.getProperty("s3.endpoint");
        }
        if (s3Endpoint == null) {
            System.out.println("S3_ENDPOINT not set, using default AWS S3");
        }

        s3Bucket = System.getenv("S3_BUCKET");
        if (s3Bucket == null) {
            s3Bucket = System.getProperty("s3.bucket");
        }
        if (s3Bucket == null) {
            System.err.println("Error: S3_BUCKET must be set via environment or -Ds3.bucket");
            System.exit(1);
        }
        System.out.println("Using S3 bucket: " + s3Bucket);
    }

    @Override
    protected Map<String, String> getTransportWriteOptions() {
        Map<String, String> options = new HashMap<>();
        options.put("data_transport", "S3_COMPAT");

        if (s3Endpoint != null) {
            options.put("storage_client_endpoint_override", s3Endpoint);
        }

        // S3 transport tuning options
        // Chunk size for multipart uploads (default 100MB, can be tuned for performance)
        options.put("storage_client_max_chunk_size_in_bytes",
            String.valueOf(100 * 1024 * 1024)); // 100MB

        // Maximum SSTable bundle size for S3 transport (default 5GB)
        options.put("max_size_per_sstable_bundle_in_bytes_s3_transport",
            String.valueOf(5L * 1024 * 1024 * 1024)); // 5GB

        return options;
    }
}
