package com.rustyrazorblade.easycasslab.commands

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import com.beust.jcommander.ParametersDelegate
import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.commands.delegates.ReleaseFlag
import com.rustyrazorblade.easycasslab.containers.Packer

@Parameters(commandDescription = "Build the Cassandra image.")
class BuildCassandraImage(val context: Context) : ICommand {
    @ParametersDelegate
    var releaseFlag = ReleaseFlag()

    @Parameter(description = "AWS region to build the image in", names = ["--region", "-r"])
    var region = ""

    override fun execute() {
        val packer = Packer(context, "cassandra")

        if (region.isBlank())
            region = context.userConfig.region

        packer.build("cassandra.pkr.hcl", releaseFlag, region)
    }
}
