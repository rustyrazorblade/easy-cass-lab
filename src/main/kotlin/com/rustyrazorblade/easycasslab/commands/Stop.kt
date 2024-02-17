package com.rustyrazorblade.easycasslab.commands

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import  com.rustyrazorblade.easycasslab.Context
import  com.rustyrazorblade.easycasslab.configuration.ServerType
import  com.rustyrazorblade.easycasslab.containers.Pssh
import java.io.File

@Parameters(commandDescription = "Stop cassandra on all nodes via service command")
class Stop(val context: Context) : ICommand {

    override fun execute() {
        context.requireSshKey()

        println("Stopping cassandra service on all nodes.")
        context.requireSshKey()
        val cassandraHosts = context.tfstate.getHosts(ServerType.Cassandra)
        cassandraHosts.map {
            context.executeRemotely(it, "sudo service cassandra stop")
        }

    }
}