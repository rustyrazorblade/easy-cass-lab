package com.rustyrazorblade.easycasslab.commands

import com.beust.jcommander.Parameters
import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.containers.Packer

@Parameters(commandDescription = "Build the base image.")
class BuildBaseImage(val context: Context) : ICommand {
    override fun execute() {
        val packer = Packer(context)

        packer.build("base.pkr.hcl")
    }
}
