package com.rustyrazorblade.easycasslab.providers.ssh

/**
 * Configuration interface for SSH connections.
 * Provides all necessary settings for establishing SSH connections.
 */
interface SSHConfiguration {
    companion object {
        const val DEFAULT_CONNECTION_TIMEOUT_SECONDS = 60L
        const val DEFAULT_SSH_PORT = 22
        const val DEFAULT_KEEPALIVE_INTERVAL_SECONDS = 30L
        const val DEFAULT_SESSION_TIMEOUT_MINUTES = 10L
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

    /**
     * Keepalive interval in seconds.
     * Sends heartbeat messages to prevent session timeout.
     * Default: 30 seconds
     */
    val keepAliveIntervalSeconds: Long
        get() = DEFAULT_KEEPALIVE_INTERVAL_SECONDS

    /**
     * Session idle timeout in minutes.
     * Sessions idle longer than this will be considered dead.
     * Default: 10 minutes
     */
    val sessionTimeoutMinutes: Long
        get() = DEFAULT_SESSION_TIMEOUT_MINUTES
}
