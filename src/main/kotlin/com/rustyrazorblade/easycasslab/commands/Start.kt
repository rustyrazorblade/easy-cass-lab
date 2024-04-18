package com.rustyrazorblade.easycasslab.commands

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import com.github.ajalt.mordant.TermColors
import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.configuration.ServerType

@Parameters(commandDescription = "Start cassandra on all nodes via service command")
class Start(val context: Context) : ICommand {

    @Parameter(names = ["--sleep"], description = "Time to sleep between starts in seconds")
    var sleep : Long  = 5

    override fun execute() {
        context.requireSshKey()
        val cassandraHosts = context.tfstate.getHosts(ServerType.Cassandra)
        cassandraHosts.map {
            with(TermColors()) {
                println(green("Starting $it"))
                context.executeRemotely(it, "/usr/local/bin/restart-cassandra-and-wait")
                println("Sleeping for $sleep seconds to stagger cluster joins.")
                Thread.sleep(sleep * 1000)

            }
        }

        if (context.userConfig.axonOpsOrg.isNotBlank() && context.userConfig.axonOpsKey.isNotBlank()) {
            StartAxonOps(context).execute()
        }
    }
}