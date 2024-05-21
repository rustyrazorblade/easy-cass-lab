package com.rustyrazorblade.easycasslab.commands

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import com.github.ajalt.mordant.TermColors
import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.configuration.ServerType
import org.apache.logging.log4j.core.jmx.Server

@Parameters(commandDescription = "Start cassandra on all nodes via service command")
class Start(val context: Context) : ICommand {

    @Parameter(names = ["--sleep"], description = "Time to sleep between starts in seconds")
    var sleep : Long  = 120

    @Parameter(description = "Hosts to run this on, leave blank for all hosts.", names = ["--hosts"])
    var hosts = ""

    override fun execute() {
        context.requireSshKey()

        with(TermColors()) {
            context.tfstate.withHosts(ServerType.Cassandra, hosts) {
                println(green("Starting $it"))
                context.executeRemotely(it, "sudo systemctl start cassandra")
                println("Sleeping for $sleep seconds to stagger cluster joins.")
                Thread.sleep(sleep * 1000)
                context.executeRemotely(it, "sudo systemctl start cassandra-sidecar")
            }
        }

        if (context.userConfig.axonOpsOrg.isNotBlank() && context.userConfig.axonOpsKey.isNotBlank()) {
            StartAxonOps(context).execute()
        }
    }
}