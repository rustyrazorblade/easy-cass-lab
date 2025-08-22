package com.rustyrazorblade.easycasslab.providers.ssh

/**
 * Default implementation of SSHConfiguration.
 *
 * @param keyPath Path to the SSH private key file
 * @param connectionTimeoutSeconds Optional connection timeout (default: 60)
 * @param sshPort Optional SSH port (default: 22)
 * @param sshUsername Optional SSH username (default: "ubuntu")
 */
data class DefaultSSHConfiguration(
    override val keyPath: String,
    override val connectionTimeoutSeconds: Long = 60L,
    override val sshPort: Int = 22,
    override val sshUsername: String = "ubuntu",
) : SSHConfiguration
