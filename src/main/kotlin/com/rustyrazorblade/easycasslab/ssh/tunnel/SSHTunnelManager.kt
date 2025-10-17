package com.rustyrazorblade.easycasslab.ssh.tunnel

import com.rustyrazorblade.easycasslab.configuration.Host

/**
 * Manager for SSH port forwarding tunnels.
 * Caches tunnels by (Host, remotePort) to avoid creating duplicate connections.
 */
interface SSHTunnelManager : AutoCloseable {
    /**
     * Create or retrieve a cached SSH tunnel through the given Host.
     * Tunnels are cached by TunnelKey(host, remotePort).
     *
     * @param host The SSH host to tunnel through
     * @param remotePort Port to forward to on the remote side
     * @param remoteHost Target host from SSH server's perspective (default "localhost")
     * @param localPort Local port to bind (0 = auto-allocate)
     * @return The created or cached tunnel
     */
    fun createTunnel(
        host: Host,
        remotePort: Int,
        remoteHost: String = "localhost",
        localPort: Int = 0
    ): SSHTunnel

    /**
     * Get an existing tunnel from cache.
     *
     * @param host The SSH host
     * @param remotePort The remote port
     * @return The cached tunnel or null if not found
     */
    fun getTunnel(host: Host, remotePort: Int): SSHTunnel?

    /**
     * Close a specific tunnel and remove from cache.
     *
     * @param tunnel The tunnel to close
     */
    fun closeTunnel(tunnel: SSHTunnel)

    /**
     * Close all tunnels and clear cache.
     */
    override fun close()
}
