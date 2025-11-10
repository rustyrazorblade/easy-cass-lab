package com.rustyrazorblade.easycasslab.providers.ssh

import com.rustyrazorblade.easycasslab.configuration.Host
import com.rustyrazorblade.easycasslab.ssh.ISSHClient
import com.rustyrazorblade.easycasslab.ssh.SSHClient
import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.sshd.client.SshClient
import org.apache.sshd.common.PropertyResolverUtils
import org.apache.sshd.common.keyprovider.KeyIdentityProvider
import org.apache.sshd.common.util.security.SecurityUtils
import org.koin.core.component.KoinComponent
import java.io.IOException
import java.security.KeyPair
import java.time.Duration
import kotlin.io.path.Path

/**
 * Default implementation of SSHConnectionProvider.
 * Manages a pool of SSH connections to multiple hosts.
 *
 * @param config SSH configuration settings
 */
class DefaultSSHConnectionProvider(
    private val config: SSHConfiguration,
) : SSHConnectionProvider,
    KoinComponent {
    companion object {
        private val log = KotlinLogging.logger {}
    }

    private val connections = mutableMapOf<Host, ISSHClient>()
    private val keyPairs: List<KeyPair>
    private val sshClient: SshClient

    init {
        log.info { "Initializing SSH connection provider with key: ${config.keyPath}" }

        // Load key pairs
        val loader = SecurityUtils.getKeyPairResourceParser()
        keyPairs = loader.loadKeyPairs(null, Path(config.keyPath), null).toList()

        // Set up SSH client
        sshClient = SshClient.setUpDefaultClient()
        sshClient.setKeyIdentityProvider(KeyIdentityProvider.wrapKeyPairs(keyPairs))

        // Configure keepalive to prevent session timeouts (heartbeat in milliseconds)
        val heartbeatInterval = Duration.ofSeconds(config.keepAliveIntervalSeconds).toMillis()
        PropertyResolverUtils.updateProperty(sshClient, "heartbeat-interval", heartbeatInterval)

        // Configure session idle timeout (in milliseconds)
        val idleTimeout = Duration.ofMinutes(config.sessionTimeoutMinutes).toMillis()
        PropertyResolverUtils.updateProperty(sshClient, "idle-timeout", idleTimeout)

        sshClient.start()

        log.info { "SSH client initialized successfully with keepalive=${config.keepAliveIntervalSeconds}s" }
    }

    override fun getConnection(host: Host): ISSHClient {
        // Check if existing connection is still valid
        val existing = connections[host]
        if (existing != null) {
            if (!isSessionValid(existing)) {
                log.warn { "Session to ${host.alias} is no longer valid, will reconnect" }
                connections.remove(host)
                @Suppress("TooGenericExceptionCaught")
                try {
                    existing.close()
                } catch (e: IOException) {
                    log.debug(e) { "IO error closing stale session to ${host.alias}" }
                } catch (e: RuntimeException) {
                    log.debug(e) { "Runtime error closing stale session to ${host.alias}" }
                }
            } else {
                return existing
            }
        }

        // Create new connection
        return connections.getOrPut(host) {
            createNewConnection(host)
        }
    }

    /**
     * Check if an SSH client's session is still valid and open.
     *
     * @param client The SSH client to check
     * @return true if the session is open and authenticated, false otherwise
     */
    private fun isSessionValid(client: ISSHClient): Boolean = client.isSessionOpen()

    /**
     * Create a new SSH connection to a host.
     *
     * @param host The host to connect to
     * @return A new SSH client connected to the host
     */
    private fun createNewConnection(host: Host): ISSHClient {
        log.info { "Creating new SSH connection to ${host.alias} (${host.public})" }

        val session =
            sshClient
                .connect(
                    config.sshUsername,
                    host.public,
                    config.sshPort,
                ).verify(Duration.ofSeconds(config.connectionTimeoutSeconds))
                .session

        session.addPublicKeyIdentity(keyPairs.first())
        session.auth().verify()

        log.info { "SSH connection established to ${host.alias}" }
        return SSHClient(session)
    }

    @Suppress("TooGenericExceptionCaught")
    override fun stop() {
        log.info { "Stopping SSH client and closing ${connections.size} connections" }

        connections.values.forEach { connection ->
            try {
                connection.close()
            } catch (e: IOException) {
                log.error(e) { "IO error while closing SSH connection" }
            } catch (e: RuntimeException) {
                log.error(e) { "Runtime error while closing SSH connection" }
            }
        }
        connections.clear()

        try {
            sshClient.stop()
        } catch (e: IOException) {
            log.error(e) { "IO error while stopping SSH client" }
        } catch (e: RuntimeException) {
            log.error(e) { "Runtime error while stopping SSH client" }
        }

        log.info { "SSH client stopped successfully" }
    }
}
