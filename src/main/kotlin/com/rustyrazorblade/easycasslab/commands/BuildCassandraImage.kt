package com.rustyrazorblade.easycasslab.commands

import com.beust.jcommander.Parameters
import com.beust.jcommander.ParametersDelegate
import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.commands.delegates.ReleaseFlag
import com.rustyrazorblade.easycasslab.containers.Packer

@Parameters(commandDescription = "Build the Cassandra image.")
class BuildCassandraImage(val context: Context) : ICommand {
    @ParametersDelegate
    var releaseFlag = ReleaseFlag()

    override fun execute() {
        val packer = Packer(context)
        packer.build("cassandra.pkr.hcl", releaseFlag)
    }
}
