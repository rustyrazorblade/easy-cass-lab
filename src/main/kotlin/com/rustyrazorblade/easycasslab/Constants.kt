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
        const val REMOTE_DOCKER_COMPOSE = "$REMOTE_HOME/docker-compose.yaml"
        const val REMOTE_ENV_FILE = "$REMOTE_HOME/.env"
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

    // Docker configuration
    object Docker {
        const val CONTAINER_ID_DISPLAY_LENGTH = 12
        const val CONTAINER_POLLING_INTERVAL_MS = 1000L
        const val FRAME_REPORTING_INTERVAL = 100

        // Docker commands
        object Commands {
            const val COMPOSE_PULL = "docker compose pull"
            const val COMPOSE_UP_DETACHED = "docker compose up -d"
            const val COMPOSE_PS = "docker compose ps"
            const val VERSION_CHECK = "which docker && docker --version"
        }

        // Docker images
        object Images {
            const val OTEL_COLLECTOR = "otel/opentelemetry-collector-contrib:latest"
        }
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
        // Control node configs
        const val CONTROL_DOCKER_COMPOSE = "control/docker-compose.yaml"
        const val CONTROL_OTEL_CONFIG = "control/otel-collector-config.yaml"

        // Cassandra node configs
        const val CASSANDRA_DOCKER_COMPOSE = "cassandra/docker-compose.yaml"
        const val CASSANDRA_OTEL_CONFIG = "cassandra/otel-cassandra-config.yaml"
        const val CASSANDRA_REMOTE_OTEL_CONFIG = "otel-cassandra-config.yaml"

        // Stress node configs
        const val STRESS_DOCKER_COMPOSE = "stress/docker-compose.yaml"
        const val STRESS_OTEL_CONFIG = "stress/otel-stress-config.yaml"
        const val STRESS_REMOTE_OTEL_CONFIG = "otel-stress-config.yaml"
    }

    // Remote file existence checks
    object RemoteChecks {
        const val FILE_EXISTS_SUFFIX = "&& echo 'exists' || echo 'not found'"
        const val EXISTS_RESPONSE = "exists"
        const val NOT_FOUND_RESPONSE = "not found"
    }
}
