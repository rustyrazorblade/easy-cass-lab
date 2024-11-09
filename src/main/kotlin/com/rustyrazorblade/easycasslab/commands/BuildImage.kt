package com.rustyrazorblade.easycasslab.commands

import com.beust.jcommander.Parameters
import com.beust.jcommander.ParametersDelegate
import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.commands.delegates.BuildArgs

@Parameters(commandDescription = "Build both the base and Cassandra image.")
class BuildImage(val context: Context) : ICommand {
    @ParametersDelegate
    var buildArgs = BuildArgs(context)

    override fun execute() {
        BuildBaseImage(context)
            .apply {
                this.buildArgs=this@BuildImage.buildArgs
            }
            .execute()
        BuildCassandraImage(context)
            .apply {
                this.buildArgs=this@BuildImage.buildArgs
            }
            .execute()
    }
}
