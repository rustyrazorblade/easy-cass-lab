package com.rustyrazorblade.easycasslab.commands

import com.beust.jcommander.Parameters
import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.annotations.McpCommand
import com.rustyrazorblade.easycasslab.configuration.ServerType

@McpCommand
@Parameters(commandDescription = "List available versions", commandNames = ["list", "ls"])
class ListVersions(context: Context) : BaseCommand(context) {
    override fun execute() {
        tfstate.getHosts(ServerType.Cassandra).first().let {
            val response = remoteOps.executeRemotely(it, "ls /usr/local/cassandra", output = false)
            response.text.split("\n")
                .filter { !it.equals("current") }
                .forEach { outputHandler.handleMessage(it) }
        }
    }
}
