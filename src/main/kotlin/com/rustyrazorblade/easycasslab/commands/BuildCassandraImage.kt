package com.rustyrazorblade.easycasslab.commands

import com.beust.jcommander.Parameters
import com.beust.jcommander.ParametersDelegate
import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.annotations.RequireDocker
import com.rustyrazorblade.easycasslab.commands.delegates.BuildArgs
import com.rustyrazorblade.easycasslab.containers.Packer
import org.koin.core.component.KoinComponent

@RequireDocker
@Parameters(commandDescription = "Build the Cassandra image.")
class BuildCassandraImage(
    val context: Context,
) : ICommand,
    KoinComponent {
    @ParametersDelegate var buildArgs = BuildArgs()

    override fun execute() {
        val packer = Packer(context, "cassandra")
        packer.build("cassandra.pkr.hcl", buildArgs)
    }
}
