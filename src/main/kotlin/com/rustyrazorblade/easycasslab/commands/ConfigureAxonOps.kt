package com.rustyrazorblade.easycasslab.commands

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.configuration.ServerType

@Parameters(commandDescription = "setup / configure axon-agent for use with the Cassandra cluster")
class ConfigureAxonOps(val context: Context) : ICommand {

    @Parameter(description = "AxonOps Organization Name", names = ["--org"])
    var org = ""

    @Parameter(description = "AxonOps API Key", names = ["--key"])
    var key = ""

    override fun execute() {
        context.requireSshKey()

        val axonOrg = if (org.isNotBlank()) org else context.userConfig.axonOpsOrg
        val axonKey = if (key.isNotBlank()) key else context.userConfig.axonOpsKey
        if ((axonOrg.isBlank() || axonKey.isBlank())) {
            println("--org and --key are required")
            System.exit(1)
        }

        context.tfstate.withHosts(ServerType.Cassandra) {
            println("Configure axonops on $it")

            context.executeRemotely(it, "/usr/local/bin/setup-axonops $axonOrg $axonKey")
        }
    }
}