package com.rustyrazorblade.easycasslab.ssh.tunnel

import com.rustyrazorblade.easycasslab.configuration.Host
import com.rustyrazorblade.easycasslab.output.OutputHandler
import com.rustyrazorblade.easycasslab.providers.ssh.SSHConnectionProvider
import io.github.oshai.kotlinlogging.KotlinLogging
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.net.ServerSocket
import java.util.concurrent.ConcurrentHashMap

/**
 * Default implementation of SSHTunnelManager that caches tunnels by (Host, remotePort).
 * Uses Apache MINA SSHD for actual SSH port forwarding.
 */
class DefaultSSHTunnelManager(
    private val sshConnectionProvider: SSHConnectionProvider
) : SSHTunnelManager, KoinComponent {

    private val tunnelCache = ConcurrentHashMap<TunnelKey, SSHTunnel>()
    // Map to track which SSH client is handling each tunnel for cleanup
    private val tunnelClients = ConcurrentHashMap<TunnelKey, Host>()
    private val outputHandler: OutputHandler by inject()
    private val log = KotlinLogging.logger {}

    override fun createTunnel(
        host: Host,
        remotePort: Int,
        remoteHost: String,
        localPort: Int
    ): SSHTunnel {
        val key = TunnelKey(host, remotePort)

        return tunnelCache.computeIfAbsent(key) {
            log.debug { "Creating new SSH tunnel through ${host.alias} to port $remotePort" }
            outputHandler.handleMessage(
                "Creating SSH tunnel through ${host.alias} to $remoteHost:$remotePort"
            )

            // Create actual SSH port forward
            val sshClient = sshConnectionProvider.getConnection(host)
            val actualLocalPort = try {
                val requestedPort = if (localPort == 0) 0 else localPort
                sshClient.createLocalPortForward(requestedPort, remoteHost, remotePort)
            } catch (e: Exception) {
                outputHandler.handleError("Failed to create tunnel: ${e.message}")
                throw e
            }

            // Store the client reference for cleanup
            tunnelClients[key] = host

            outputHandler.handleMessage(
                "Tunnel created: localhost:$actualLocalPort -> ${host.alias} -> $remoteHost:$remotePort"
            )

            SSHTunnel(
                host = host,
                remotePort = remotePort,
                localPort = actualLocalPort,
                remoteHost = remoteHost,
                isActive = true,
                tracker = actualLocalPort  // Store the local port as the tracker for cleanup
            )
        }
    }

    override fun getTunnel(host: Host, remotePort: Int): SSHTunnel? {
        val key = TunnelKey(host, remotePort)
        return tunnelCache[key]
    }

    override fun closeTunnel(tunnel: SSHTunnel) {
        val key = TunnelKey(tunnel.host, tunnel.remotePort)
        tunnelCache.remove(key)
        tunnel.isActive = false

        // Close the actual port forward
        tunnelClients[key]?.let { host ->
            try {
                val sshClient = sshConnectionProvider.getConnection(host)
                val localPort = tunnel.tracker as? Int
                if (localPort != null) {
                    sshClient.closeLocalPortForward(localPort)
                }
            } catch (e: Exception) {
                log.error(e) { "Error closing port forward for tunnel on port ${tunnel.localPort}" }
            }
        }
        tunnelClients.remove(key)

        log.debug { "Closed tunnel through ${tunnel.host.alias} to port ${tunnel.remotePort}" }
        outputHandler.handleMessage(
            "Closed tunnel through ${tunnel.host.alias} to ${tunnel.remoteHost}:${tunnel.remotePort}"
        )
    }

    override fun close() {
        log.info { "Closing ${tunnelCache.size} SSH tunnels" }
        outputHandler.handleMessage("Closing all SSH tunnels")

        // Close all tunnels properly
        tunnelCache.values.forEach { tunnel ->
            closeTunnel(tunnel)
        }
        tunnelCache.clear()
        tunnelClients.clear()
    }
}
