package com.rustyrazorblade.easycasslab.commands

import com.beust.jcommander.Parameters
import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.containers.Packer

@Parameters(commandDescription = "Build both the base and Cassandra image.")
class BuildImage(val context: Context) : ICommand {
    override fun execute() {
        BuildBaseImage(context).execute()
        BuildCassandraImage(context).execute()
    }
}
