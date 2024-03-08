package com.rustyrazorblade.easycasslab.commands

import com.beust.jcommander.Parameters
import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.configuration.ServerType
import java.nio.file.Path

@Parameters(commandDescription = "Runs setup_instance.sh on all Cassandra instances")
class SetupInstance(val context: Context) : ICommand {
    override fun execute() {
        context.tfstate.withHosts(ServerType.Stress) {
            context.executeRemotely(it, "sudo hostnamectl set-hostname ${it.alias}")
        }
        context.tfstate.withHosts(ServerType.Cassandra) {
            context.executeRemotely(it, "sudo hostnamectl set-hostname ${it.alias}")
            context.upload(it, Path.of("setup_instance.sh"), "setup_instance.sh")
            context.executeRemotely(it, "sudo bash setup_instance.sh")
        }
    }
}