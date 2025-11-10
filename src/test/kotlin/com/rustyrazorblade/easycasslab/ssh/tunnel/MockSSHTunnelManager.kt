package com.rustyrazorblade.easycasslab.ssh.tunnel

import com.rustyrazorblade.easycasslab.configuration.Host
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Mock implementation of SSHTunnelManager for testing.
 * Provides the same caching behavior without actual SSH connections.
 */
class MockSSHTunnelManager : SSHTunnelManager {
    val tunnels = ConcurrentHashMap<TunnelKey, SSHTunnel>()
    private val portCounter = AtomicInteger(10000)

    override fun createTunnel(
        host: Host,
        remotePort: Int,
        remoteHost: String,
        localPort: Int,
    ): SSHTunnel {
        val key = TunnelKey(host, remotePort)
        return tunnels.computeIfAbsent(key) {
            SSHTunnel(
                host = host,
                remotePort = remotePort,
                localPort = if (localPort == 0) portCounter.getAndIncrement() else localPort,
                remoteHost = remoteHost,
                isActive = true,
            )
        }
    }

    override fun getTunnel(
        host: Host,
        remotePort: Int,
    ): SSHTunnel? = tunnels[TunnelKey(host, remotePort)]

    override fun closeTunnel(tunnel: SSHTunnel) {
        tunnels.remove(TunnelKey(tunnel.host, tunnel.remotePort))
        tunnel.isActive = false
    }

    override fun close() {
        tunnels.values.forEach { it.isActive = false }
        tunnels.clear()
    }
}
