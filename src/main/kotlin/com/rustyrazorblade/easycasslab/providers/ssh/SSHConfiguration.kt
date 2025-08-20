package com.rustyrazorblade.easycasslab.providers.ssh

/**
 * Configuration interface for SSH connections.
 * Provides all necessary settings for establishing SSH connections.
 */
interface SSHConfiguration {
    /**
     * Path to the SSH private key file used for authentication.
     */
    val keyPath: String
    
    /**
     * Connection timeout in seconds.
     * Default: 60 seconds
     */
    val connectionTimeoutSeconds: Long
        get() = 60L
    
    /**
     * SSH port to connect to.
     * Default: 22
     */
    val sshPort: Int
        get() = 22
    
    /**
     * Default SSH username.
     * Default: "ubuntu"
     */
    val sshUsername: String
        get() = "ubuntu"
}