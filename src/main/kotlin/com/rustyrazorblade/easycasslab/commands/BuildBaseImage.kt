package com.rustyrazorblade.easycasslab.commands

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import com.beust.jcommander.ParametersDelegate
import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.commands.delegates.ReleaseFlag
import com.rustyrazorblade.easycasslab.containers.Packer

@Parameters(commandDescription = "Build the base image.")
class BuildBaseImage(val context: Context) : ICommand {
    @ParametersDelegate
    var releaseFlag = ReleaseFlag()

    @Parameter(description = "AWS region to build the image in", names = ["--region", "-r"])
    var region = ""

    override fun execute() {
        val packer = Packer(context, "base")

        if (region.isBlank())
            region = context.userConfig.region

        packer.build("base.pkr.hcl", releaseFlag, region)
    }
}
