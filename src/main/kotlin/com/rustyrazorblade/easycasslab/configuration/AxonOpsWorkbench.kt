package com.rustyrazorblade.easycasslab.configuration

import kotlinx.serialization.SerialName
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
                        workspaceId = "",
                        name = clusterName,
                        // Default Cassandra datacenter name
                        datacenter = "datacenter1",
                        // Use private IP for internal connectivity
                        hostname = host.private,
                        // Default Cassandra CQL port
                        port = "9042",
                        timestampGenerator = "",
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
                        // Use public IP for SSH connectivity
                        host = host.public,
                        // Default SSH port
                        port = "22",
                        // Default EC2 user
                        username = "ubuntu",
                        password = "",
                        privatekey = userConfig.sshKeyPath,
                        passphrase = "",
                        // Destination is the private IP
                        destaddr = host.private,
                        // Destination port is CQL port
                        destport = "9042",
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
    @SerialName("workspace_id")
    val workspaceId: String,
    val name: String,
    val datacenter: String,
    val hostname: String,
    val port: String,
    @SerialName("timestamp_generator")
    val timestampGenerator: String,
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
