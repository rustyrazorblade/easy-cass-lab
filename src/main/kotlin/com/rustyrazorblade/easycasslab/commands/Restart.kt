package com.rustyrazorblade.easycasslab.commands

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import com.beust.jcommander.ParametersDelegate
import com.github.ajalt.mordant.TermColors
import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.commands.delegates.Hosts
import com.rustyrazorblade.easycasslab.configuration.ServerType

@Parameters(commandDescription = "Restart cassandra", commandNames = ["restart"])
class Restart(val context: Context) : ICommand {
    @ParametersDelegate
    var hosts = Hosts()

    override fun execute() {
        // TODO wait for cassandra to become available
        println("Restarting cassandra service on all nodes.")
        with(TermColors()) {
            context.tfstate.withHosts(ServerType.Cassandra, hosts) {
                println(green("Restarting $it"))
                context.executeRemotely(it, "/usr/local/bin/restart-cassandra-and-wait").text
            }
        }
    }
}