package com.rustyrazorblade.easycasslab.ssh.tunnel

import com.rustyrazorblade.easycasslab.BaseKoinTest
import com.rustyrazorblade.easycasslab.configuration.Host
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap

class SSHTunnelManagerTest : BaseKoinTest() {
    private lateinit var tunnelManager: MockSSHTunnelManager
    private val host1 = Host(
        public = "54.1.2.3",
        private = "10.0.0.1",
        alias = "cassandra0",
        availabilityZone = "us-west-2a"
    )
    private val host2 = Host(
        public = "54.1.2.4",
        private = "10.0.0.2",
        alias = "cassandra1",
        availabilityZone = "us-west-2a"
    )

    @BeforeEach
    fun setup() {
        tunnelManager = MockSSHTunnelManager()
    }

    @Test
    fun `caches tunnel for same Host and remotePort combination`() {
        // When creating tunnel twice with same Host and remotePort
        val tunnel1 = tunnelManager.createTunnel(host1, 9042)
        val tunnel2 = tunnelManager.createTunnel(host1, 9042)

        // Then returns same cached instance
        assertThat(tunnel1).isSameAs(tunnel2)
        assertThat(tunnelManager.tunnels).hasSize(1)
    }

    @Test
    fun `creates different tunnels for same Host with different remote ports`() {
        // Given same Host but different remote ports
        val cassandraTunnel = tunnelManager.createTunnel(host1, 9042)
        val monitoringTunnel = tunnelManager.createTunnel(host1, 3000)

        // Then creates two separate tunnels
        assertThat(cassandraTunnel).isNotSameAs(monitoringTunnel)
        assertThat(cassandraTunnel.remotePort).isEqualTo(9042)
        assertThat(monitoringTunnel.remotePort).isEqualTo(3000)
        assertThat(cassandraTunnel.localPort).isNotEqualTo(monitoringTunnel.localPort)
        assertThat(tunnelManager.tunnels).hasSize(2)
    }

    @Test
    fun `creates different tunnels for different Hosts with same remote port`() {
        // Given different hosts with same remote port
        val tunnel1 = tunnelManager.createTunnel(host1, 9042)
        val tunnel2 = tunnelManager.createTunnel(host2, 9042)

        // Then creates two separate tunnels
        assertThat(tunnel1).isNotSameAs(tunnel2)
        assertThat(tunnel1.host).isEqualTo(host1)
        assertThat(tunnel2.host).isEqualTo(host2)
        assertThat(tunnel1.localPort).isNotEqualTo(tunnel2.localPort)
        assertThat(tunnelManager.tunnels).hasSize(2)
    }

    @Test
    fun `handles concurrent tunnel creation safely`() {
        // Given multiple threads requesting same Host+remotePort
        val executor = Executors.newFixedThreadPool(10)
        val latch = CountDownLatch(10)
        val createdTunnels = ConcurrentHashMap.newKeySet<SSHTunnel>()

        // When all threads try to create tunnel simultaneously
        repeat(10) {
            executor.submit {
                try {
                    val tunnel = tunnelManager.createTunnel(host1, 9042)
                    createdTunnels.add(tunnel)
                } finally {
                    latch.countDown()
                }
            }
        }

        // Then only one tunnel is created
        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue()
        assertThat(createdTunnels).hasSize(1)
        assertThat(tunnelManager.tunnels).hasSize(1)

        executor.shutdown()
    }

    @Test
    fun `allocates unique local ports when auto-allocating`() {
        // When creating multiple tunnels with localPort = 0 (auto-allocate)
        val tunnel1 = tunnelManager.createTunnel(host1, 9042, localPort = 0)
        val tunnel2 = tunnelManager.createTunnel(host1, 3000, localPort = 0)
        val tunnel3 = tunnelManager.createTunnel(host2, 9042, localPort = 0)

        // Then each gets a unique local port
        val localPorts = setOf(tunnel1.localPort, tunnel2.localPort, tunnel3.localPort)
        assertThat(localPorts).hasSize(3)
        assertThat(tunnel1.localPort).isGreaterThan(0)
        assertThat(tunnel2.localPort).isGreaterThan(0)
        assertThat(tunnel3.localPort).isGreaterThan(0)
    }

    @Test
    fun `removes from cache when closed`() {
        // Given an active tunnel
        val tunnel = tunnelManager.createTunnel(host1, 9042)
        assertThat(tunnel.isActive).isTrue()
        assertThat(tunnelManager.getTunnel(host1, 9042)).isNotNull()

        // When closing the tunnel
        tunnelManager.closeTunnel(tunnel)

        // Then removed from cache and marked inactive
        assertThat(tunnel.isActive).isFalse()
        assertThat(tunnelManager.getTunnel(host1, 9042)).isNull()
        assertThat(tunnelManager.tunnels).isEmpty()

        // And a new tunnel can be created with same key
        val newTunnel = tunnelManager.createTunnel(host1, 9042)
        assertThat(newTunnel).isNotSameAs(tunnel)
        assertThat(newTunnel.isActive).isTrue()
    }

    @Test
    fun `closes all tunnels on manager shutdown`() {
        // Given multiple active tunnels
        val tunnel1 = tunnelManager.createTunnel(host1, 9042)
        val tunnel2 = tunnelManager.createTunnel(host1, 3000)
        val tunnel3 = tunnelManager.createTunnel(host2, 9042)

        assertThat(tunnel1.isActive).isTrue()
        assertThat(tunnel2.isActive).isTrue()
        assertThat(tunnel3.isActive).isTrue()
        assertThat(tunnelManager.tunnels).hasSize(3)

        // When closing the manager
        tunnelManager.close()

        // Then all tunnels are marked inactive and cache is cleared
        assertThat(tunnel1.isActive).isFalse()
        assertThat(tunnel2.isActive).isFalse()
        assertThat(tunnel3.isActive).isFalse()
        assertThat(tunnelManager.tunnels).isEmpty()
    }

    @Test
    fun `getTunnel returns null when tunnel does not exist`() {
        // When getting a tunnel that doesn't exist
        val tunnel = tunnelManager.getTunnel(host1, 9042)

        // Then returns null
        assertThat(tunnel).isNull()
    }

    @Test
    fun `getTunnel returns cached tunnel when it exists`() {
        // Given an existing tunnel
        val createdTunnel = tunnelManager.createTunnel(host1, 9042)

        // When getting the tunnel
        val retrievedTunnel = tunnelManager.getTunnel(host1, 9042)

        // Then returns the same tunnel
        assertThat(retrievedTunnel).isSameAs(createdTunnel)
    }

    @Test
    fun `respects explicit local port when specified`() {
        // When creating tunnel with explicit local port
        val tunnel = tunnelManager.createTunnel(host1, 9042, localPort = 12345)

        // Then uses the specified port
        assertThat(tunnel.localPort).isEqualTo(12345)
    }
}
