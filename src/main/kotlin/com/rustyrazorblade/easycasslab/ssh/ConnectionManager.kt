package com.rustyrazorblade.easycasslab.ssh

import com.rustyrazorblade.easycasslab.configuration.Host
import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.sshd.client.SshClient
import org.apache.sshd.common.keyprovider.KeyIdentityProvider
import org.apache.sshd.common.util.security.SecurityUtils
import kotlin.io.path.Path
import java.security.KeyPair
import java.time.Duration

/**
 * Manages SSH connections to multiple hosts
 */
open class ConnectionManager(val keyPath: String) {
    private val log = KotlinLogging.logger {}
    private val keyPairs: List<KeyPair>
    private val sshClient: SshClient
    private val connections = mutableMapOf<Host, ISSHClient>()
    
    init {
        // Load key pairs
        val loader = SecurityUtils.getKeyPairResourceParser()
        keyPairs = loader.loadKeyPairs(null, Path(keyPath), null).toList()
        
        // Set up SSH client
        sshClient = SshClient.setUpDefaultClient()
        sshClient.setKeyIdentityProvider(KeyIdentityProvider.wrapKeyPairs(keyPairs))
        sshClient.start()
    }

    /**
     * Get a connection for the given host, creating one if it doesn't exist
     */
    open fun getConnection(host: Host): ISSHClient {
        return connections.getOrPut(host) {
            val session = sshClient.connect("ubuntu", host.public, 22)
                .verify(Duration.ofSeconds(60))
                .session
            session.addPublicKeyIdentity(keyPairs.first())
            session.auth().verify()
            SSHClient(session)
        }
    }
    
    /**
     * Close all connections and stop the SSH client
     */
    fun stop() {
        log.debug { "Stopping SSH client and closing all connections" }
        connections.values.forEach { it.close() }
        connections.clear()
        sshClient.stop()
    }
}