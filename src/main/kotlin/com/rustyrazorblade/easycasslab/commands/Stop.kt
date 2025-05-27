package com.rustyrazorblade.easycasslab.commands

import com.beust.jcommander.Parameters
import com.beust.jcommander.ParametersDelegate
import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.commands.delegates.Hosts
import com.rustyrazorblade.easycasslab.configuration.ServerType

@Parameters(commandDescription = "Stop cassandra on all nodes via service command")
class Stop(val context: Context) : ICommand {
    @ParametersDelegate
    var hosts = Hosts()

    override fun execute() {
        context.requireSshKey()

        println("Stopping cassandra service on all nodes.")
        context.requireSshKey()

        context.tfstate.withHosts(ServerType.Cassandra, hosts) {
            context.executeRemotely(it, "sudo systemctl stop cassandra").text
        }
    }
}
