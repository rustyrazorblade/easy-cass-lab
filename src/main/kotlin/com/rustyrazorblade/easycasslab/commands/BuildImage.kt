package com.rustyrazorblade.easycasslab.commands

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import com.beust.jcommander.ParametersDelegate
import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.commands.delegates.ReleaseFlag
import com.rustyrazorblade.easycasslab.containers.Packer

@Parameters(commandDescription = "Build both the base and Cassandra image.")
class BuildImage(val context: Context) : ICommand {
    @ParametersDelegate
    var releaseFlag = ReleaseFlag()

    @Parameter(description = "AWS region to build the image in", names = ["--region", "-r"])
    var region = ""

    override fun execute() {
        BuildBaseImage(context)
            .apply {
                this.releaseFlag=this@BuildImage.releaseFlag
                this.region= this@BuildImage.region
            }
            .execute()
        BuildCassandraImage(context)
            .apply {
                this.releaseFlag=this@BuildImage.releaseFlag
                this.region=this@BuildImage.region
            }
            .execute()
    }
}
