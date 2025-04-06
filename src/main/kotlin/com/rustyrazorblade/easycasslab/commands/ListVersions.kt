package com.rustyrazorblade.easycasslab.commands

import com.beust.jcommander.Parameters
import  com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.configuration.ServerType

@Parameters(commandDescription = "List available versions", commandNames = ["list", "ls"])
class ListVersions(val context: Context) : ICommand {
    override fun execute() {
        context.tfstate.getHosts(ServerType.Cassandra).first().let {
            val response = context.executeRemotely(it, "ls /usr/local/cassandra", output = false)
            response.text.split("\n")
                .filter { !it.equals("current") }
                .forEach { println(it) }
        }
    }
}