package com.rustyrazorblade.easydblab.commands

import com.rustyrazorblade.easydblab.Context
import com.rustyrazorblade.easydblab.annotations.McpCommand
import com.rustyrazorblade.easydblab.annotations.RequireProfileSetup
import com.rustyrazorblade.easydblab.configuration.ServerType
import com.rustyrazorblade.easydblab.configuration.getHosts
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
        clusterState.getHosts(ServerType.Cassandra).first().let {
            val response = remoteOps.executeRemotely(it, "ls /usr/local/cassandra", output = false)
            response.text.split("\n").filter { line -> line != "current" }.forEach { line ->
                outputHandler.handleMessage(line)
            }
        }
    }
}
