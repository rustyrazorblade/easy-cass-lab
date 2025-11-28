package com.rustyrazorblade.easycasslab.commands

import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.annotations.McpCommand
import com.rustyrazorblade.easycasslab.annotations.RequireProfileSetup
import com.rustyrazorblade.easycasslab.configuration.ServerType
import picocli.CommandLine.Command

/**
 * Lists available Cassandra versions installed on the cluster.
 */
@McpCommand
@RequireProfileSetup
@Command(
    name = "list",
    aliases = ["ls"],
    description = ["List available versions"],
)
class ListVersions(
    context: Context,
) : PicoBaseCommand(context) {
    override fun execute() {
        tfstate.getHosts(ServerType.Cassandra).first().let {
            val response = remoteOps.executeRemotely(it, "ls /usr/local/cassandra", output = false)
            response.text.split("\n").filter { line -> line != "current" }.forEach { line ->
                outputHandler.handleMessage(line)
            }
        }
    }
}
