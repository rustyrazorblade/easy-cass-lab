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
        const val SETUP_TIMEOUT_SECONDS = 60
    }

    // Retry configuration
    object Retry {
        const val MAX_PERMISSION_CHECK_RETRIES = 30
        const val MAX_OPERATION_RETRIES = 3

        // IAM operations: Higher retry count due to eventual consistency
        const val MAX_INSTANCE_PROFILE_RETRIES = 5

        // S3 operations: Standard retry count for AWS service errors
        const val MAX_S3_RETRIES = 3
        const val RETRY_DELAY_MS = 2000L
        const val RETRY_BACKOFF_MULTIPLIER = 3

        // Base delay for exponential backoff (1s, 2s, 4s, 8s...)
        const val EXPONENTIAL_BACKOFF_BASE_MS = 1000L
    }

    // File and directory permissions (octal equivalents in decimal)
    object FilePermissions {
        const val FILE_READ_WRITE = 420 // Octal 0644
        const val FILE_READ_ONLY = 256 // Octal 0400
        const val DIR_USER_ONLY = 448 // Octal 0700
    }

    // Exit codes
    object ExitCodes {
        const val ERROR = 1
    }

    // Network configuration
    object Network {
        const val DEFAULT_MCP_PORT = 8888
        const val EASY_CASS_MCP_PORT = 8000
        const val CASSANDRA_EASY_STRESS_PORT = 9000
        const val OPENSEARCH_PORT = 9200
        const val OPENSEARCH_DASHBOARDS_PORT = 5601
        const val HTTPS_PORT = 443
        const val HTTP_PORT = 80
        const val SSH_PORT = 22
        const val CASSANDRA_NATIVE_PORT = 7000
        const val CASSANDRA_JMX_PORT = 3000
        const val CASSANDRA_JMX_PORT_END = 16001
    }

    // HTTP Status Codes
    object HttpStatus {
        const val FORBIDDEN = 403
        const val NOT_FOUND = 404
        const val SERVER_ERROR_MIN = 500
        const val SERVER_ERROR_MAX = 599
        const val CLIENT_ERROR_THRESHOLD = 500 // Errors >= 500 are server errors
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

        // AMI configuration
        const val AMI_PATTERN_TEMPLATE = "rustyrazorblade/images/easy-cass-lab-cassandra-%s-*"
        const val AMI_NAME_SYSTEM_PROPERTY = "easycasslab.ami.name"
        const val AMI_OVERRIDE_ENV = "EASY_CASS_LAB_AMI"

        // Storage configuration
        const val DEFAULT_VOLUME_SIZE_GB = 1024
        const val DEFAULT_IOPS = 3000

        // IAM Role Names
        object Roles {
            const val EC2_INSTANCE_ROLE = "EasyCassLabEC2Role"
            const val EMR_SERVICE_ROLE = "EasyCassLabEMRServiceRole"
            const val EMR_EC2_ROLE = "EasyCassLabEMREC2Role"
        }
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
