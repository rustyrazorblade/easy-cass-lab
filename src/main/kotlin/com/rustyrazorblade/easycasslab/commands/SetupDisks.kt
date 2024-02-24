package com.rustyrazorblade.easycasslab.commands

import com.beust.jcommander.Parameters
import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.configuration.ServerType
import java.nio.file.Path

@Parameters(commandDescription = "Sets up the disks for the instances")
class SetupDisks(val context: Context) : ICommand {
    override fun execute() {
        context.tfstate.withHosts(ServerType.Cassandra) {
            context.upload(it, Path.of("disk_setup.sh"), "disk_setup.sh")
            context.executeRemotely(it, "sudo bash disk_setup.sh")
        }
    }
}