package com.rustyrazorblade.easycasslab.temporal.workflows

import org.apache.sshd.client.SshClient
import org.apache.sshd.client.session.ClientSession
import org.apache.sshd.common.keyprovider.KeyIdentityProvider
import org.apache.sshd.common.util.security.SecurityUtils
import java.time.Duration
import kotlin.io.path.Path

class RollingRestartActivitiesImpl : RollingRestartActivities {
    override fun restartNode(sshKeyPath: String, ip: String): Boolean {
        try {
            println("Restarting node $ip with ssh key $sshKeyPath")
            val session = getSession(sshKeyPath, ip)
            val output = session.executeRemoteCommand("/usr/local/bin/restart-cassandra-and-wait")
            println(output)
        } catch (e: Exception) {
            println("Error restarting node $ip: $e")
            return false
        }

        return true
    }

    private fun getSession(sshKeyPath: String, ip: String): ClientSession {
        val loader = SecurityUtils.getKeyPairResourceParser()
        val keyPairs = loader.loadKeyPairs(null, Path(sshKeyPath), null)

        // TODO (jwest): we are leaking clients here and should look at not creating an SSH client per activity call (although we may have to?)
        val sshClient = SshClient.setUpDefaultClient()
        sshClient.keyIdentityProvider = KeyIdentityProvider.wrapKeyPairs(keyPairs)
        sshClient.start()

        val session = sshClient.connect("ubuntu", ip, 22)
            .verify(Duration.ofSeconds(60))
            .session
        session.addPublicKeyIdentity(keyPairs.first())
        session.auth().verify()

        return session
    }
}