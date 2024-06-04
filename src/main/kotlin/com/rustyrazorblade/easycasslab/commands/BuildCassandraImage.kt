package com.rustyrazorblade.easycasslab.commands

import com.beust.jcommander.Parameters
import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.containers.Packer

@Parameters(commandDescription = "Build the Cassandra image.")
class BuildCassandraImage(val context: Context) : ICommand {
    override fun execute() {
        val packer = Packer(context)
        packer.build("cassandra.pkr.hcl")
    }
}
