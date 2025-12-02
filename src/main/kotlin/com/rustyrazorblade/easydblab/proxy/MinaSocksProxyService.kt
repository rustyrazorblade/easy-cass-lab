package com.rustyrazorblade.easydblab.proxy

import com.rustyrazorblade.easydblab.configuration.ClusterHost
import com.rustyrazorblade.easydblab.configuration.Host
import com.rustyrazorblade.easydblab.providers.ssh.SSHConnectionProvider
import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.sshd.client.session.ClientSession
import org.apache.sshd.common.util.net.SshdSocketAddress
import java.net.ServerSocket
import java.time.Instant
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Apache MINA-based SOCKS5 proxy service using SSH dynamic port forwarding.
 *
 * This service creates a SOCKS5 proxy by establishing an SSH connection to a gateway host
 * and setting up dynamic port forwarding. All traffic sent through the local proxy port
 * is tunneled through the SSH connection.
 *
 * Thread-safety: Uses ReentrantLock for safe concurrent access in server mode.
 *
 * @property sshConnectionProvider Provider for SSH connections
 */
class MinaSocksProxyService(
    private val sshConnectionProvider: SSHConnectionProvider,
) : SocksProxyService {
    companion object {
        private val log = KotlinLogging.logger {}
    }

    private val lock = ReentrantLock()
    private var session: ClientSession? = null
    private var state: SocksProxyState? = null
    private var localAddress: SshdSocketAddress? = null
    private var actualLocalPort: Int = 0

    override fun ensureRunning(gatewayHost: ClusterHost): SocksProxyState =
        lock.withLock {
            // Already running to same host? Return existing state
            if (isRunningInternal() && state?.gatewayHost?.alias == gatewayHost.alias) {
                state!!.connectionCount.incrementAndGet()
                log.debug { "Proxy already running to ${gatewayHost.alias}, reusing (connections: ${state!!.connectionCount.get()})" }
                return@withLock state!!
            }

            // Running to different host? Stop first
            if (isRunningInternal()) {
                log.info { "Proxy running to ${state?.gatewayHost?.alias}, switching to ${gatewayHost.alias}" }
                stopInternal()
            }

            return@withLock startInternal(gatewayHost)
        }

    override fun start(gatewayHost: ClusterHost): SocksProxyState =
        lock.withLock {
            if (isRunningInternal()) {
                if (state?.gatewayHost?.alias != gatewayHost.alias) {
                    error("Proxy already running to ${state?.gatewayHost?.alias}, cannot start to ${gatewayHost.alias}")
                }
                return@withLock state!!
            }
            return@withLock startInternal(gatewayHost)
        }

    private fun startInternal(gatewayHost: ClusterHost): SocksProxyState {
        // Find an available port dynamically
        val port = findAvailablePort()
        actualLocalPort = port
        localAddress = SshdSocketAddress("localhost", port)

        log.info { "Starting SOCKS5 proxy to ${gatewayHost.alias} (${gatewayHost.publicIp}) on port $port" }

        // Convert ClusterHost to Host for SSH connection provider
        val host = gatewayHost.toHost()

        // Get SSH connection and extract the underlying session
        val sshClient = sshConnectionProvider.getConnection(host)

        // The SSHClient wraps a ClientSession - we need to access it for port forwarding
        // We use reflection as a workaround since the interface doesn't expose the session directly
        val clientSession = extractClientSession(sshClient)

        // Start dynamic port forwarding (SOCKS5 proxy)
        clientSession.startDynamicPortForwarding(localAddress)

        session = clientSession
        state =
            SocksProxyState(
                localPort = port,
                gatewayHost = gatewayHost,
                startTime = Instant.now(),
            )

        log.info { "SOCKS5 proxy started successfully on localhost:$port" }
        return state!!
    }

    /**
     * Find an available local port by binding to port 0 and letting the OS assign one.
     */
    private fun findAvailablePort(): Int =
        ServerSocket(0).use { socket ->
            socket.localPort
        }

    override fun stop() {
        lock.withLock { stopInternal() }
    }

    private fun stopInternal() {
        val currentState = state
        if (currentState != null) {
            log.info { "Stopping SOCKS5 proxy to ${currentState.gatewayHost.alias}" }
        }

        session?.let { s ->
            localAddress?.let { addr ->
                @Suppress("TooGenericExceptionCaught")
                try {
                    s.stopDynamicPortForwarding(addr)
                    log.debug { "Dynamic port forwarding stopped" }
                } catch (e: Exception) {
                    log.warn(e) { "Error stopping dynamic port forwarding" }
                }
            }
        }

        session = null
        state = null
        localAddress = null
        actualLocalPort = 0
    }

    override fun isRunning(): Boolean = lock.withLock { isRunningInternal() }

    private fun isRunningInternal(): Boolean = session?.isOpen == true

    override fun getState(): SocksProxyState? = lock.withLock { state }

    override fun getLocalPort(): Int = actualLocalPort

    /**
     * Extract the ClientSession from an ISSHClient.
     * This uses reflection since the interface doesn't expose the session directly.
     */
    private fun extractClientSession(sshClient: com.rustyrazorblade.easydblab.ssh.ISSHClient): ClientSession {
        // The SSHClient class has a private 'session' field
        val sshClientClass = sshClient::class.java
        val sessionField =
            sshClientClass.getDeclaredField("session").apply {
                isAccessible = true
            }
        return sessionField.get(sshClient) as ClientSession
    }
}

/**
 * Extension function to convert ClusterHost to Host for SSH operations
 */
private fun ClusterHost.toHost(): Host =
    Host(
        public = this.publicIp,
        private = this.privateIp,
        alias = this.alias,
        availabilityZone = this.availabilityZone,
    )
