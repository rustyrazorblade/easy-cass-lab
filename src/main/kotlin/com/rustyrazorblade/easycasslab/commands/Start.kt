package com.rustyrazorblade.easycasslab.commands

import org.apache.sshd.client.SshClient
import com.beust.jcommander.Parameters
import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.configuration.ServerType
import org.apache.sshd.common.keyprovider.KeyIdentityProvider
import org.apache.sshd.common.util.security.SecurityUtils
import java.time.Duration
import kotlin.io.path.Path

@Parameters(commandDescription = "Start cassandra on all nodes via service command")
class Start(val context: Context) : ICommand {

    override fun execute() {
        context.requireSshKey()
        val cassandraHosts = context.tfstate.getHosts(ServerType.Cassandra)

        val keyPath = context.userConfig.sshKeyPath

        // Setup guide: https://github.com/apache/mina-sshd/blob/master/docs/client-setup.md

        // Create the client.
        // We have to register the keys with the client.
        // Client can be used to connect to multiple hosts
        val client = SshClient.setUpDefaultClient()
        val loader = SecurityUtils.getKeyPairResourceParser()
        val keyPairs = loader.loadKeyPairs(null, Path(keyPath), null)
        client.setKeyIdentityProvider(KeyIdentityProvider.wrapKeyPairs(keyPairs))
        client.start()

        val host = cassandraHosts.first()
        val session = client.connect("ubuntu", host.public,22)
            .verify(Duration.ofSeconds(10))
            .session
        session.addPublicKeyIdentity(keyPairs.first())
        session.executeRemoteCommand("sudo service cassandra start")
        client.stop()
    }
}