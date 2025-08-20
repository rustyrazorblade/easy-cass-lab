package com.rustyrazorblade.easycasslab.providers.ssh

import com.rustyrazorblade.easycasslab.configuration.Host
import com.rustyrazorblade.easycasslab.ssh.ISSHClient

/**
 * Provider interface for SSH connections.
 * Manages a pool of SSH connections to multiple hosts and handles their lifecycle.
 */
interface SSHConnectionProvider {
    /**
     * Get an SSH connection for the given host.
     * Creates a new connection if one doesn't exist, or returns an existing one from the pool.
     * 
     * @param host The host to connect to
     * @return An SSH client connected to the host
     */
    fun getConnection(host: Host): ISSHClient
    
    /**
     * Stop all SSH connections and clean up resources.
     * This should be called when the application is shutting down.
     */
    fun stop()
}