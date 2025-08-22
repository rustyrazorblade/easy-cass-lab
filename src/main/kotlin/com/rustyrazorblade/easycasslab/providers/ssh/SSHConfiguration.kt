package com.rustyrazorblade.easycasslab.providers.ssh

/**
 * Configuration interface for SSH connections.
 * Provides all necessary settings for establishing SSH connections.
 */
interface SSHConfiguration {
    companion object {
        const val DEFAULT_CONNECTION_TIMEOUT_SECONDS = 60L
        const val DEFAULT_SSH_PORT = 22
    }

    /**
     * Path to the SSH private key file used for authentication.
     */
    val keyPath: String

    /**
     * Connection timeout in seconds.
     * Default: 60 seconds
     */
    val connectionTimeoutSeconds: Long
        get() = DEFAULT_CONNECTION_TIMEOUT_SECONDS

    /**
     * SSH port to connect to.
     * Default: 22
     */
    val sshPort: Int
        get() = DEFAULT_SSH_PORT

    /**
     * Default SSH username.
     * Default: "ubuntu"
     */
    val sshUsername: String
        get() = "ubuntu"
}
