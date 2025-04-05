package com.rustyrazorblade.easycasslab.ssh

import com.rustyrazorblade.easycasslab.configuration.Host
import org.apache.logging.log4j.kotlin.logger
import org.apache.sshd.client.SshClient
import org.apache.sshd.client.session.ClientSession
import org.apache.sshd.common.keyprovider.KeyIdentityProvider
import org.apache.sshd.scp.client.CloseableScpClient
import org.apache.sshd.scp.client.ScpClientCreator
import java.time.Duration
import java.security.KeyPair

/**
 * Manages SSH connection to a specific host
 */
class Connection(
    private val host: Host,
    private val sshClient: SshClient,
    private val keyPairs: List<KeyPair>
) {
    private val log = logger()
    private var session: ClientSession? = null


    /**
     * Get or create an SSH session for this host
     */
    fun getSession(): ClientSession {
        return session ?: createSession().also { session = it }
    }
    
    private fun createSession(): ClientSession {
        log.debug { "Creating new SSH session for ${host.alias} (${host.public})" }
        val newSession = sshClient.connect("ubuntu", host.public, 22)
            .verify(Duration.ofSeconds(60))
            .session
        newSession.addPublicKeyIdentity(keyPairs.first())
        newSession.auth().verify()
        return newSession
    }
    
    /**
     * Get a SCP client for this host
     */
    fun getScpClient(): CloseableScpClient {
        val session = getSession()
        val creator = ScpClientCreator.instance()
        val client = creator.createScpClient(session)
        return CloseableScpClient.singleSessionInstance(client)
    }
    
    /**
     * Close the session for this host
     */
    fun close() {
        session?.close()
        session = null
    }
}