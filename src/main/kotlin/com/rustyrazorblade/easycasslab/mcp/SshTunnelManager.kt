package com.rustyrazorblade.easycasslab.mcp

import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.configuration.ServerType
import com.rustyrazorblade.easycasslab.providers.ssh.SSHConnectionProvider
import com.rustyrazorblade.easycasslab.ssh.SSHClient
import mu.KotlinLogging
import org.apache.sshd.client.session.ClientSession
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Manages SSH tunnels for secure remote MCP connections.
 * Creates and maintains persistent SSH tunnels to control nodes for MCP communication using Apache Mina.
 */
class SshTunnelManager(
    private val context: Context,
    private val sshConnectionProvider: SSHConnectionProvider
) {
    private val log = KotlinLogging.logger {}

    companion object {
        private const val TUNNEL_PORT_START = 20000
    }

    // Track active tunnels: nodeName -> TunnelInfo
    private val activeTunnels = ConcurrentHashMap<String, TunnelInfo>()

    // Track local port allocation (start from 20000 to avoid conflicts)
    private val nextLocalPort = AtomicInteger(TUNNEL_PORT_START)

    /**
     * Information about an active SSH tunnel
     */
    data class TunnelInfo(
        val nodeName: String,
        val remoteHost: String,
        val remotePort: Int,
        val localPort: Int,
        val localAddress: org.apache.sshd.common.util.net.SshdSocketAddress? = null
    )

    /**
     * Establishes SSH tunnels to all control nodes for MCP communication.
     * Returns a map of node names to tunnel information.
     */
    fun establishTunnels(remoteServers: List<RemoteMcpDiscovery.RemoteServer>): Map<String, TunnelInfo> {
        log.info { "Establishing SSH tunnels for ${remoteServers.size} remote MCP servers" }

        val tunnels = mutableMapOf<String, TunnelInfo>()

        remoteServers.forEach { server ->
            try {
                val tunnel = createTunnel(server)
                tunnels[server.nodeName] = tunnel
                activeTunnels[server.nodeName] = tunnel
                log.info {
                    "Established tunnel for ${server.nodeName}: localhost:${tunnel.localPort} -> " +
                        "${server.host}:${server.port}"
                }
            } catch (e: Exception) {
                log.error(e) { "Failed to establish tunnel for ${server.nodeName}" }
            }
        }

        return tunnels
    }

    /**
     * Creates an SSH tunnel for a specific remote server using Apache Mina port forwarding.
     */
    private fun createTunnel(server: RemoteMcpDiscovery.RemoteServer): TunnelInfo {
        val localPort = allocateLocalPort()

        // Get the control node host information
        val controlHosts = context.tfstate.getHosts(ServerType.Control)
        val controlHost = controlHosts.find { it.alias == server.nodeName }
            ?: throw IllegalArgumentException("Control host ${server.nodeName} not found in terraform state")

        log.debug { "Creating SSH tunnel for ${server.nodeName}: localhost:$localPort -> localhost:${server.port}" }

        // Get SSH connection to the control node
        val sshClient = sshConnectionProvider.getConnection(controlHost)

        // For SSHClient, we need to access the underlying session
        // This is a bit of a hack, but we need the ClientSession for port forwarding
        val session = if (sshClient is SSHClient) {
            // Use reflection to get the private session field
            val sessionField = SSHClient::class.java.getDeclaredField("session")
            sessionField.isAccessible = true
            sessionField.get(sshClient) as ClientSession
        } else {
            error("SSH client is not a SSHClient instance, cannot create tunnel")
        }

        // Create local port forward using Apache Mina
        // Format: localPort -> remoteHost:remotePort
        // We're forwarding to localhost on the remote machine since MCP runs in Docker there
        val localAddress = session.startLocalPortForwarding(
            localPort,
            org.apache.sshd.common.util.net.SshdSocketAddress("localhost", server.port)
        )

        log.info { "SSH tunnel established: localhost:$localPort -> ${controlHost.public}:localhost:${server.port}" }

        return TunnelInfo(
            nodeName = server.nodeName,
            remoteHost = server.host,
            remotePort = server.port,
            localPort = localPort,
            localAddress = localAddress
        )
    }

    /**
     * Allocates a local port for tunneling.
     */
    private fun allocateLocalPort(): Int {
        return nextLocalPort.getAndIncrement()
    }

    /**
     * Gets the local tunneled port for a given node.
     */
    fun getLocalPort(nodeName: String): Int? {
        return activeTunnels[nodeName]?.localPort
    }

    /**
     * Gets tunnel information for a given node.
     */
    fun getTunnelInfo(nodeName: String): TunnelInfo? {
        return activeTunnels[nodeName]
    }

    /**
     * Checks if a tunnel is active for a given node.
     */
    fun isTunnelActive(nodeName: String): Boolean {
        val tunnel = activeTunnels[nodeName] ?: return false

        // Try to connect to the local port to verify tunnel is working
        return try {
            java.net.Socket("localhost", tunnel.localPort).use { socket ->
                socket.isConnected
            }
        } catch (e: Exception) {
            log.debug { "Tunnel for $nodeName appears to be down: ${e.message}" }
            false
        }
    }

    /**
     * Closes all active SSH tunnels.
     */
    fun closeAllTunnels() {
        log.info { "Closing ${activeTunnels.size} SSH tunnels" }

        activeTunnels.forEach { (nodeName, tunnel) ->
            try {
                closeTunnel(tunnel)
                log.debug { "Closed tunnel for $nodeName" }
            } catch (e: Exception) {
                log.error(e) { "Failed to close tunnel for $nodeName" }
            }
        }

        activeTunnels.clear()
    }

    /**
     * Closes a specific SSH tunnel by stopping the local port forwarding.
     */
    private fun closeTunnel(tunnel: TunnelInfo) {
        try {
            // Get the control node host
            val controlHosts = context.tfstate.getHosts(ServerType.Control)
            val controlHost = controlHosts.find { it.alias == tunnel.nodeName }
                ?: throw IllegalArgumentException("Control host ${tunnel.nodeName} not found")

            val sshClient = sshConnectionProvider.getConnection(controlHost)

            // Access the underlying session
            val session = if (sshClient is SSHClient) {
                val sessionField = SSHClient::class.java.getDeclaredField("session")
                sessionField.isAccessible = true
                sessionField.get(sshClient) as ClientSession
            } else {
                error("SSH client is not a SSHClient instance")
            }

            // Stop the port forwarding
            if (tunnel.localAddress != null) {
                session.stopLocalPortForwarding(tunnel.localAddress)
                log.debug { "Stopped port forwarding for ${tunnel.nodeName}" }
            }
        } catch (e: Exception) {
            log.warn(e) { "Error closing tunnel for ${tunnel.nodeName}" }
        }
    }

    /**
     * Re-establishes a tunnel if it's not active.
     */
    fun ensureTunnelActive(server: RemoteMcpDiscovery.RemoteServer): TunnelInfo {
        if (!isTunnelActive(server.nodeName)) {
            log.info { "Re-establishing tunnel for ${server.nodeName}" }
            val tunnel = createTunnel(server)
            activeTunnels[server.nodeName] = tunnel
            return tunnel
        }

        return activeTunnels[server.nodeName]!!
    }
}
