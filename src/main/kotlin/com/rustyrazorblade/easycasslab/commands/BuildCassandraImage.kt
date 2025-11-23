package com.rustyrazorblade.easycasslab.commands

import com.beust.jcommander.Parameters
import com.beust.jcommander.ParametersDelegate
import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.annotations.RequireDocker
import com.rustyrazorblade.easycasslab.annotations.RequireProfileSetup
import com.rustyrazorblade.easycasslab.commands.delegates.BuildArgs
import com.rustyrazorblade.easycasslab.configuration.User
import com.rustyrazorblade.easycasslab.containers.Packer
import com.rustyrazorblade.easycasslab.providers.aws.PackerInfrastructureService
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@RequireDocker
@RequireProfileSetup
@Parameters(commandDescription = "Build the Cassandra image.")
class BuildCassandraImage(
    val context: Context,
) : ICommand,
    KoinComponent {
    @ParametersDelegate var buildArgs = BuildArgs()

    private val packerInfrastructure: PackerInfrastructureService by inject()
    private val userConfig: User by inject()

    override fun execute() {
        // Populate buildArgs from userConfig if not overridden by command-line args
        if (buildArgs.region.isBlank()) {
            buildArgs.region = userConfig.region
        }

        // Ensure Packer VPC infrastructure exists before building
        packerInfrastructure.ensureInfrastructure()

        val packer = Packer(context, "cassandra")
        packer.build("cassandra.pkr.hcl", buildArgs)
    }
}
