package com.rustyrazorblade.easycasslab.commands

import com.beust.jcommander.Parameters
import com.beust.jcommander.ParametersDelegate
import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.commands.delegates.Hosts
import com.rustyrazorblade.easycasslab.configuration.Host
import com.rustyrazorblade.easycasslab.configuration.ServerType
import java.nio.file.Path

@Parameters(commandDescription = "Runs setup_instance.sh on all Cassandra instances")
class SetupInstance(context: Context) : BaseCommand(context) {
    @ParametersDelegate var hosts = Hosts()

    override fun execute() {
        fun setup(host: Host) {
            remoteOps.upload(host, Path.of("environment.sh"), "environment.sh")
            remoteOps.executeRemotely(host, "sudo mv environment.sh /etc/profile.d/stress.sh").text
        }

        tfstate.withHosts(ServerType.Stress, hosts, parallel = true) {
            setup(it)
            remoteOps.executeRemotely(it, "sudo hostnamectl set-hostname ${it.alias}").text
            remoteOps.upload(it, Path.of("setup_instance.sh"), "setup_instance.sh")
            remoteOps.executeRemotely(it, "sudo bash setup_instance.sh").text
        }
        tfstate.withHosts(ServerType.Cassandra, Hosts.all()) {
            setup(it)
            remoteOps.executeRemotely(it, "sudo hostnamectl set-hostname ${it.alias}").text
            remoteOps.upload(it, Path.of("setup_instance.sh"), "setup_instance.sh")
            remoteOps.executeRemotely(it, "sudo bash setup_instance.sh").text
        }
    }
}
