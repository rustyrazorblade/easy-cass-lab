package com.rustyrazorblade.easycasslab.commands

import com.beust.jcommander.Parameters
import com.beust.jcommander.ParametersDelegate
import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.annotations.RequireSSHKey
import com.rustyrazorblade.easycasslab.commands.delegates.Hosts
import com.rustyrazorblade.easycasslab.configuration.ServerType

// @McpCommand
@RequireSSHKey
@Parameters(commandDescription = "Stop cassandra on all nodes via service command")
class Stop(context: Context) : BaseCommand(context) {
    @ParametersDelegate
    var hosts = Hosts()

    override fun execute() {

        outputHandler.handleMessage("Stopping cassandra service on all nodes.")

        tfstate.withHosts(ServerType.Cassandra, hosts) {
            remoteOps.executeRemotely(it, "sudo systemctl stop cassandra").text
        }
    }
}
