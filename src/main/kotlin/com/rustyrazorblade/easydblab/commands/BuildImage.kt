package com.rustyrazorblade.easydblab.commands

import com.rustyrazorblade.easydblab.Context
import com.rustyrazorblade.easydblab.annotations.RequireDocker
import com.rustyrazorblade.easydblab.annotations.RequireProfileSetup
import com.rustyrazorblade.easydblab.commands.mixins.BuildArgsMixin
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

    override fun execute() {
        BuildBaseImage(context).apply { this.buildArgs = this@BuildImage.buildArgs }.execute()
        BuildCassandraImage(context).apply { this.buildArgs = this@BuildImage.buildArgs }.execute()
    }
}
