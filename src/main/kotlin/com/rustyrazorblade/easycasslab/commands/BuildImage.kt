package com.rustyrazorblade.easycasslab.commands

import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.annotations.RequireDocker
import com.rustyrazorblade.easycasslab.annotations.RequireProfileSetup
import com.rustyrazorblade.easycasslab.commands.mixins.BuildArgsMixin
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
