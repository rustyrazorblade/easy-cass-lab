package com.rustyrazorblade.easycasslab.commands

import com.beust.jcommander.Parameters
import com.beust.jcommander.ParametersDelegate
import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.annotations.RequireSSHKey
import com.rustyrazorblade.easycasslab.commands.delegates.Hosts
import com.rustyrazorblade.easycasslab.configuration.ServerType

@RequireSSHKey
@Parameters(commandDescription = "Start axon-agent on all nodes via service command")
class StartAxonOps(
    context: Context,
) : BaseCommand(context) {
    @ParametersDelegate var hosts = Hosts()

    override fun execute() {
        tfstate.withHosts(ServerType.Cassandra, Hosts.all(), parallel = true) {
            remoteOps.executeRemotely(it, "sudo systemctl start axon-agent").text
        }
    }
}
