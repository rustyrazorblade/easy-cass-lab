package com.rustyrazorblade.easycasslab.commands

import com.beust.jcommander.Parameters
import com.beust.jcommander.ParametersDelegate
import com.github.ajalt.mordant.TermColors
import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.annotations.McpCommand
import com.rustyrazorblade.easycasslab.commands.delegates.Hosts
import com.rustyrazorblade.easycasslab.configuration.ServerType

@McpCommand
@Parameters(commandDescription = "Restart cassandra", commandNames = ["restart"])
class Restart(
    context: Context,
) : BaseCommand(context) {
    @ParametersDelegate var hosts = Hosts()

    override fun execute() {
        // TODO wait for cassandra to become available
        outputHandler.handleMessage("Restarting cassandra service on all nodes.")
        with(TermColors()) {
            tfstate.withHosts(ServerType.Cassandra, hosts) {
                outputHandler.handleMessage(green("Restarting $it"))
                remoteOps.executeRemotely(it, "/usr/local/bin/restart-cassandra-and-wait").text
            }
        }
    }
}
