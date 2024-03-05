package com.rustyrazorblade.easycasslab.commands

import com.beust.jcommander.Parameters
import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.configuration.ServerType

@Parameters(commandDescription = "Restart cassandra", commandNames = ["restart"])
class Restart(val context: Context) : ICommand {
    override fun execute() {
        // TODO wait for cassandra to become available
        println("Restarting cassandra service on all nodes.")
        context.tfstate.withHosts(ServerType.Cassandra) {
            context.executeRemotely(it, "sudo systemctl restart cassandra")
        }
    }
}