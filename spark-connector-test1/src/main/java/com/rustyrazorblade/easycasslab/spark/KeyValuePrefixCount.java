package com.rustyrazorblade.easycasslab.spark;

import com.datastax.oss.driver.api.core.CqlSession;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;

import java.net.InetSocketAddress;

import static org.apache.spark.sql.functions.col;
import static org.apache.spark.sql.functions.substring;

public class KeyValuePrefixCount {
    public static void main(String[] args) {
        // Parse command line arguments
        String cassandraHost = args.length > 0 ? args[0] : "127.0.0.1";

        // Create the output table if it doesn't exist using the Java driver
        try (CqlSession session = CqlSession.builder()
                .addContactPoint(new InetSocketAddress(cassandraHost, 9042))
                .withLocalDatacenter("us-west-2")
                .build()) {

            session.execute(
                "CREATE TABLE IF NOT EXISTS cassandra_easy_stress.keyvalue_prefix_count " +
                "(prefix text PRIMARY KEY, frequency bigint)"
            );

            // Truncate to ensure fresh results
            session.execute("TRUNCATE cassandra_easy_stress.keyvalue_prefix_count");
        }

        SparkSession spark = SparkSession.builder()
            .appName("KeyValuePrefixCount")
            .config("spark.cassandra.connection.host", cassandraHost)
            .config("spark.sql.extensions", "com.datastax.spark.connector.CassandraSparkExtensions")
            .config("spark.sql.catalog.cassandra", "com.datastax.spark.connector.datasource.CassandraCatalog")
            .getOrCreate();

        // Read from keyvalue table
        Dataset<Row> keyValueDF = spark.read()
            .format("org.apache.spark.sql.cassandra")
            .option("keyspace", "cassandra_easy_stress")
            .option("table", "keyvalue")
            .load();

        // Extract first 3 characters of value and count frequencies
        Dataset<Row> prefixCounts = keyValueDF
            .select(substring(col("value"), 1, 3).as("prefix"))
            .groupBy("prefix")
            .count()
            .withColumnRenamed("count", "frequency");

        // Write results to keyvalue_prefix_count table
        prefixCounts.write()
            .format("org.apache.spark.sql.cassandra")
            .option("keyspace", "cassandra_easy_stress")
            .option("table", "keyvalue_prefix_count")
            .mode("append")
            .save();

        System.out.println("Processed " + prefixCounts.count() + " unique prefixes");

        spark.stop();
    }
}
