package com.rustyrazorblade.easycasslab.configuration

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * AxonOps Workbench configuration model.
 * This configuration file is used by AxonOps Workbench to connect to Cassandra clusters.
 */
@Serializable
data class AxonOpsWorkbenchConfig(
    val basic: BasicConfig,
    val auth: AuthConfig,
    val ssl: SslConfig,
    val ssh: SshConfig,
) {
    companion object {
        /**
         * Creates a JSON instance with pretty printing for serialization
         */
        val json =
            Json {
                prettyPrint = true
                encodeDefaults = true
            }

        /**
         * Creates an AxonOps Workbench configuration for a given host and user configuration
         *
         * @param host The Cassandra host to connect to (typically cassandra0)
         * @param userConfig The user configuration containing SSH key information
         * @param clusterName The name of the Cassandra cluster
         * @return A configured AxonOpsWorkbenchConfig instance
         */
        fun create(
            host: Host,
            userConfig: User,
            clusterName: String = "easy-cass-lab",
        ): AxonOpsWorkbenchConfig {
            return AxonOpsWorkbenchConfig(
                basic =
                    BasicConfig(
                        workspace_id = "",
                        name = clusterName,
                        datacenter = "datacenter1", // Default Cassandra datacenter name
                        hostname = host.private, // Use private IP for internal connectivity
                        port = "9042", // Default Cassandra CQL port
                        timestamp_generator = "",
                        cqlshrc = "",
                    ),
                auth =
                    AuthConfig(
                        username = "",
                        password = "",
                    ),
                ssl =
                    SslConfig(
                        ssl = "",
                        certfile = "",
                        userkey = "",
                        usercert = "",
                        validate = "",
                    ),
                ssh =
                    SshConfig(
                        host = host.public, // Use public IP for SSH connectivity
                        port = "22", // Default SSH port
                        username = "ubuntu", // Default EC2 user
                        password = "",
                        privatekey = userConfig.sshKeyPath,
                        passphrase = "",
                        destaddr = host.private, // Destination is the private IP
                        destport = "9042", // Destination port is CQL port
                    ),
            )
        }

        /**
         * Writes the configuration to a JSON file
         *
         * @param config The configuration to write
         * @param file The file to write to
         */
        fun writeToFile(
            config: AxonOpsWorkbenchConfig,
            file: File,
        ) {
            val jsonString = json.encodeToString(config)
            file.writeText(jsonString)
        }
    }
}

@Serializable
data class BasicConfig(
    val workspace_id: String,
    val name: String,
    val datacenter: String,
    val hostname: String,
    val port: String,
    val timestamp_generator: String,
    val cqlshrc: String,
)

@Serializable
data class AuthConfig(
    val username: String,
    val password: String,
)

@Serializable
data class SslConfig(
    val ssl: String,
    val certfile: String,
    val userkey: String,
    val usercert: String,
    val validate: String,
)

@Serializable
data class SshConfig(
    val host: String,
    val port: String,
    val username: String,
    val password: String,
    val privatekey: String,
    val passphrase: String,
    val destaddr: String,
    val destport: String,
)
