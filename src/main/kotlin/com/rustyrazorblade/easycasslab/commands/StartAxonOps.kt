package com.rustyrazorblade.easycasslab.commands

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.configuration.ServerType

@Parameters(commandDescription =  "Start axon-agent on all nodes via service command")
class StartAxonOps(val context: Context) : ICommand {
    @Parameter(description = "Hosts to run this on, leave blank for all hosts.", names = ["--hosts"])
    var hosts = ""

    override fun execute() {
        context.requireSshKey()
        context.tfstate.withHosts(ServerType.Cassandra, "") {
            context.executeRemotely(it, "sudo systemctl start axon-agent")
        }
    }
}