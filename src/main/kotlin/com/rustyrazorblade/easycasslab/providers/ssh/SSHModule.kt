package com.rustyrazorblade.easycasslab.providers.ssh

import com.rustyrazorblade.easycasslab.mcp.SshTunnelManager
import org.koin.dsl.module

/**
 * Koin module for SSH-related dependency injection.
 *
 * Provides:
 * - SSHConnectionProvider as a singleton (manages connection pool)
 * - RemoteOperationsService as a factory (stateless operations)
 * - SshTunnelManager as a singleton (manages MCP SSH tunnels)
 *
 * Note: SSHConfiguration must be provided by another module (e.g., contextModule)
 */
val sshModule =
    module {
        // SSH connection provider - singleton because it manages a connection pool
        single<SSHConnectionProvider> { DefaultSSHConnectionProvider(get()) }

        // Remote operations service - factory because it's stateless
        factory<RemoteOperationsService> { DefaultRemoteOperationsService(get()) }

        // SSH tunnel manager - singleton because it manages persistent tunnels
        single { SshTunnelManager(get(), get()) }
    }
