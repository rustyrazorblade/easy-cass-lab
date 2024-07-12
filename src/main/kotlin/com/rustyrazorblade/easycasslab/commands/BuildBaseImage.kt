package com.rustyrazorblade.easycasslab.commands

import com.beust.jcommander.Parameters
import com.beust.jcommander.ParametersDelegate
import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.commands.delegates.ReleaseFlag
import com.rustyrazorblade.easycasslab.containers.Packer

@Parameters(commandDescription = "Build the base image.")
class BuildBaseImage(val context: Context) : ICommand {
    @ParametersDelegate
    var releaseFlag = ReleaseFlag()

    override fun execute() {
        val packer = Packer(context)

        packer.build("base.pkr.hcl", releaseFlag)
    }
}
