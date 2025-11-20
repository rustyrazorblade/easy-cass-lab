package com.rustyrazorblade.easycasslab

/**
 * Central location for all constants used throughout the application
 */
object Constants {
    // Container paths and directories
    object Paths {
        const val LOCAL_MOUNT = "/local"
        const val TERRAFORM_CACHE = "/tcache"
        const val CREDENTIALS_MOUNT = "/credentials"
        const val AWS_CREDENTIALS_MOUNT = "/awscredentials"

        // Remote paths
        const val REMOTE_HOME = "/home/ubuntu"
    }

    // Server types
    object Servers {
        const val CASSANDRA = "cassandra"
        const val STRESS = "stress"
        const val CONTROL = "control"
    }

    // Time-related constants
    object Time {
        const val MILLIS_PER_SECOND = 1000L
        const val THREAD_SLEEP_DELAY_MS = 10L
        const val THREAD_JOIN_TIMEOUT_MS = 1000L
        const val OTEL_STARTUP_DELAY_MS = 2000L
    }

    // Network configuration
    object Network {
        const val DEFAULT_MCP_PORT = 8888
        const val EASY_CASS_MCP_PORT = 8000
        const val CASSANDRA_EASY_STRESS_PORT = 9000
        const val OPENSEARCH_PORT = 9200
        const val OPENSEARCH_DASHBOARDS_PORT = 5601
    }

    // Docker configuration (for local container operations)
    object Docker {
        const val CONTAINER_ID_DISPLAY_LENGTH = 12
        const val CONTAINER_POLLING_INTERVAL_MS = 1000L
        const val FRAME_REPORTING_INTERVAL = 100
    }

    // Terraform configuration
    object Terraform {
        const val PLUGIN_CACHE_DIR_ENV = "TF_PLUGIN_CACHE_DIR"
        const val AUTO_APPROVE_FLAG = "-auto-approve"
    }

    // Packer configuration
    object Packer {
        const val CASSANDRA_VERSIONS_FILE = "cassandra_versions.yaml"
        const val AWS_CREDENTIALS_ENV = "AWS_SHARED_CREDENTIALS_FILE"
    }

    // AWS configuration
    object AWS {
        const val DEFAULT_CREDENTIALS_NAME = "awscredentials"
        const val SSH_KEY_ENV = "EASY_CASS_LAB_SSH_KEY"
    }

    // Monitoring
    object Monitoring {
        const val PROMETHEUS_JOB_CASSANDRA = "cassandra"
        const val PROMETHEUS_JOB_STRESS = "stress"
    }

    // Configuration file paths
    object ConfigPaths {
        // Cassandra node configs
        const val CASSANDRA_SIDECAR_CONFIG = "cassandra/cassandra-sidecar.yaml"
        const val CASSANDRA_REMOTE_SIDECAR_DIR = "/etc/cassandra-sidecar"
        const val CASSANDRA_REMOTE_SIDECAR_CONFIG = "$CASSANDRA_REMOTE_SIDECAR_DIR/cassandra-sidecar.yaml"
    }

    // Cassandra Sidecar configuration
    object Sidecar {
        const val STORAGE_DIR = "/mnt/cassandra/data"
        const val STAGING_DIR = "/mnt/cassandra/import"
    }

    // Remote file existence checks
    object RemoteChecks {
        const val FILE_EXISTS_SUFFIX = "&& echo 'exists' || echo 'not found'"
        const val EXISTS_RESPONSE = "exists"
        const val NOT_FOUND_RESPONSE = "not found"
    }

    // K3s configuration
    object K3s {
        const val REMOTE_KUBECONFIG = "/etc/rancher/k3s/k3s.yaml"
        const val LOCAL_KUBECONFIG = "kubeconfig"
        const val NODE_TOKEN_PATH = "/var/lib/rancher/k3s/server/node-token"
        const val DEFAULT_SERVER_URL = "https://127.0.0.1:6443"
    }
}
