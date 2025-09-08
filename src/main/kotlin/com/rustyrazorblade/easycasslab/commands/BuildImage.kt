package com.rustyrazorblade.easycasslab.commands

import com.beust.jcommander.Parameters
import com.beust.jcommander.ParametersDelegate
import com.rustyrazorblade.easycasslab.annotations.RequireDocker
import com.rustyrazorblade.easycasslab.commands.delegates.BuildArgs

@RequireDocker
@Parameters(commandDescription = "Build both the base and Cassandra image.")
class BuildImage : ICommand {
    @ParametersDelegate
    var buildArgs = BuildArgs()

    override fun execute() {
        BuildBaseImage()
            .apply {
                this.buildArgs = this@BuildImage.buildArgs
            }
            .execute()
        BuildCassandraImage()
            .apply {
                this.buildArgs = this@BuildImage.buildArgs
            }
            .execute()
    }
}
