package com.rustyrazorblade.easydblab.commands

import com.rustyrazorblade.easydblab.Context
import com.rustyrazorblade.easydblab.annotations.RequireDocker
import com.rustyrazorblade.easydblab.annotations.RequireProfileSetup
import com.rustyrazorblade.easydblab.commands.mixins.BuildArgsMixin
import com.rustyrazorblade.easydblab.services.CommandExecutor
import org.koin.core.component.inject
import picocli.CommandLine.Command
import picocli.CommandLine.Mixin

/**
 * Build both the base and Cassandra AMI images.
 */
@RequireDocker
@RequireProfileSetup
@Command(
    name = "build-image",
    description = ["Build both the base and Cassandra image"],
)
class BuildImage(
    context: Context,
) : PicoBaseCommand(context) {
    @Mixin
    var buildArgs = BuildArgsMixin()

    private val commandExecutor: CommandExecutor by inject()

    override fun execute() {
        commandExecutor.execute {
            BuildBaseImage(context).apply { this.buildArgs = this@BuildImage.buildArgs }
        }
        commandExecutor.execute {
            BuildCassandraImage(context).apply { this.buildArgs = this@BuildImage.buildArgs }
        }
    }
}
