package com.rustyrazorblade.easycasslab.commands

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import com.beust.jcommander.ParametersDelegate
import com.github.ajalt.mordant.TermColors
import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.commands.delegates.Hosts
import com.rustyrazorblade.easycasslab.configuration.ServerType

@Parameters(commandDescription = "Start cassandra on all nodes via service command")
class Start(val context: Context) : ICommand {
    @Parameter(names = ["--sleep"], description = "Time to sleep between starts in seconds")
    var sleep: Long = 120

    @ParametersDelegate
    var hosts = Hosts()

    override fun execute() {
        context.requireSshKey()

        with(TermColors()) {
            context.tfstate.withHosts(ServerType.Cassandra, hosts) {
                println(green("Starting $it"))
                context.executeRemotely(it, "sudo systemctl start cassandra").text
                println("Cassandra started, waiting for up/normal")
                context.executeRemotely(it, "sudo wait-for-up-normal").text
                context.executeRemotely(it, "sudo systemctl start cassandra-sidecar").text
            }
        }

        if (context.userConfig.axonOpsOrg.isNotBlank() && context.userConfig.axonOpsKey.isNotBlank()) {
            StartAxonOps(context).execute()
        }
    }
}
