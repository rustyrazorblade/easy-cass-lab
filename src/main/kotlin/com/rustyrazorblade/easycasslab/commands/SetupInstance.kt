package com.rustyrazorblade.easycasslab.commands

import com.beust.jcommander.Parameters
import com.beust.jcommander.ParametersDelegate
import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.commands.delegates.Hosts
import com.rustyrazorblade.easycasslab.configuration.Host
import com.rustyrazorblade.easycasslab.configuration.ServerType
import java.nio.file.Path

@Parameters(commandDescription = "Runs setup_instance.sh on all Cassandra instances")
class SetupInstance(val context: Context) : ICommand {
    @ParametersDelegate
    var hosts = Hosts()

    override fun execute() {
        fun setup(host: Host) {
            context.upload(host, Path.of("environment.sh"), "environment.sh")
            context.executeRemotely(host, "sudo mv environment.sh /etc/profile.d/stress.sh")
        }

        context.tfstate.withHosts(ServerType.Stress, hosts) {
            setup(it)
            context.executeRemotely(it, "sudo hostnamectl set-hostname ${it.alias}")
            context.upload(it, Path.of("setup_instance.sh"), "setup_instance.sh")
            context.executeRemotely(it, "sudo bash setup_instance.sh")
        }
        context.tfstate.withHosts(ServerType.Cassandra, Hosts.all()) {
            setup(it)
            context.executeRemotely(it, "sudo hostnamectl set-hostname ${it.alias}")
            context.upload(it, Path.of("setup_instance.sh"), "setup_instance.sh")
            context.executeRemotely(it, "sudo bash setup_instance.sh")
        }
    }
}