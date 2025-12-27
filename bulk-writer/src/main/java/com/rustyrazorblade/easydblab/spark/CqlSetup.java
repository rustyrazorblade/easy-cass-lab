package com.rustyrazorblade.easydblab.spark;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.CqlSessionBuilder;
import com.datastax.oss.driver.api.core.config.DefaultDriverOption;
import com.datastax.oss.driver.api.core.config.DriverConfigLoader;

import java.net.InetSocketAddress;
import java.time.Duration;

/**
 * Handles CQL session creation and DDL execution for bulk writer setup.
 * Creates keyspace and table if they don't exist before bulk write begins.
 */
public class CqlSetup implements AutoCloseable {

    private static final int CQL_PORT = 9042;
    private static final long CONNECTION_TIMEOUT_SECONDS = 30;
    private static final long REQUEST_TIMEOUT_SECONDS = 60;

    private final CqlSession session;
    private final String localDc;

    /**
     * Create a CQL session connected to Cassandra.
     *
     * @param contactPoints Comma-separated list of Cassandra host IPs
     * @param localDc Datacenter name for load balancing
     */
    public CqlSetup(String contactPoints, String localDc) {
        this.localDc = localDc;
        System.out.println("Connecting to Cassandra for DDL setup: " + contactPoints);

        DriverConfigLoader configLoader = DriverConfigLoader.programmaticBuilder()
            .withDuration(DefaultDriverOption.CONNECTION_CONNECT_TIMEOUT,
                Duration.ofSeconds(CONNECTION_TIMEOUT_SECONDS))
            .withDuration(DefaultDriverOption.REQUEST_TIMEOUT,
                Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
            .withString(DefaultDriverOption.LOAD_BALANCING_LOCAL_DATACENTER, localDc)
            .build();

        CqlSessionBuilder builder = CqlSession.builder()
            .withConfigLoader(configLoader);

        for (String host : contactPoints.split(",")) {
            builder.addContactPoint(new InetSocketAddress(host.trim(), CQL_PORT));
        }

        try {
            this.session = builder.build();
            System.out.println("Connected to Cassandra cluster");
        } catch (Exception e) {
            throw new RuntimeException(
                "Failed to connect to Cassandra at " + contactPoints +
                " (datacenter: " + localDc + "): " + e.getMessage(), e);
        }
    }

    /**
     * Create keyspace with NetworkTopologyStrategy if it doesn't exist.
     *
     * @param keyspace Keyspace name
     * @param replicationFactor Replication factor for the local datacenter
     */
    public void createKeyspace(String keyspace, int replicationFactor) {
        String cql = String.format(
            "CREATE KEYSPACE IF NOT EXISTS %s " +
            "WITH replication = {'class': 'NetworkTopologyStrategy', '%s': %d}",
            keyspace, localDc, replicationFactor);

        System.out.println("Creating keyspace: " + cql);
        try {
            session.execute(cql);
            System.out.println("Keyspace " + keyspace + " ready");
        } catch (Exception e) {
            throw new RuntimeException("Failed to create keyspace: " + cql, e);
        }
    }

    /**
     * Create table with the fixed bulk writer schema if it doesn't exist.
     * Schema: id bigint PRIMARY KEY, course blob, marks bigint
     *
     * @param keyspace Keyspace name
     * @param table Table name
     */
    public void createTable(String keyspace, String table) {
        String cql = String.format(
            "CREATE TABLE IF NOT EXISTS %s.%s (" +
            "id bigint PRIMARY KEY, " +
            "course blob, " +
            "marks bigint)",
            keyspace, table);

        System.out.println("Creating table: " + cql);
        try {
            session.execute(cql);
            System.out.println("Table " + keyspace + "." + table + " ready");
        } catch (Exception e) {
            throw new RuntimeException("Failed to create table: " + cql, e);
        }
    }

    /**
     * Convenience method to set up both keyspace and table.
     *
     * @param keyspace Keyspace name
     * @param table Table name
     * @param replicationFactor Replication factor for the local datacenter
     */
    public void setupSchema(String keyspace, String table, int replicationFactor) {
        createKeyspace(keyspace, replicationFactor);
        createTable(keyspace, table);
    }

    @Override
    public void close() {
        if (session != null && !session.isClosed()) {
            session.close();
            System.out.println("Disconnected from Cassandra");
        }
    }
}
