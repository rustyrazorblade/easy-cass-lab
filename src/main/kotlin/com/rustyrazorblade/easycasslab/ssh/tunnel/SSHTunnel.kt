package com.rustyrazorblade.easycasslab.ssh.tunnel

import com.rustyrazorblade.easycasslab.configuration.Host

/**
 * Represents an SSH tunnel from a local port through a Host to a remote destination.
 *
 * @property host The SSH host this tunnel goes through
 * @property remotePort The port to forward to on the remote side
 * @property localPort The local port that accepts connections
 * @property remoteHost The target host from the SSH server's perspective (usually "localhost")
 * @property isActive Whether this tunnel is currently active
 * @property tracker Internal Apache MINA SSHD port forwarding tracker
 */
data class SSHTunnel(
    val host: Host,
    val remotePort: Int,
    val localPort: Int,
    val remoteHost: String = "localhost",
    @Volatile var isActive: Boolean = true,
    internal val tracker: Any? = null  // Apache MINA SSHD PortForwardingTracker
)
