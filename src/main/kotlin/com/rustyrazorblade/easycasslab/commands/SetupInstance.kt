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

        fun setupStressSystemdEnv(
            host: Host,
            cassandraHost: String,
            datacenter: String,
        ) {
            // Create systemd environment file locally
            val envFile = java.io.File.createTempFile("stress", ".env")
            try {
                envFile.bufferedWriter().use { writer ->
                    writer.write("CASSANDRA_EASY_STRESS_CASSANDRA_HOST=$cassandraHost")
                    writer.newLine()
                    writer.write("CASSANDRA_EASY_STRESS_PROM_PORT=0")
                    writer.newLine()
                    writer.write("CASSANDRA_EASY_STRESS_DEFAULT_DC=$datacenter")
                    writer.newLine()
                }

                // Create directory and upload the file
                remoteOps.executeRemotely(host, "sudo mkdir -p /etc/cassandra-easy-stress").text
                remoteOps.upload(host, envFile.toPath(), "stress.env")
                remoteOps.executeRemotely(host, "sudo mv stress.env /etc/cassandra-easy-stress/stress.env").text
            } finally {
                envFile.delete()
            }
        }

        // Get datacenter once from the first stress instance (all instances are in the same DC)
        val stressHosts = tfstate.getHosts(ServerType.Stress)
        val datacenter =
            if (stressHosts.isNotEmpty()) {
                val datacenterResponse =
                    remoteOps.executeRemotely(
                        stressHosts.first(),
                        "curl -s http://169.254.169.254/latest/dynamic/instance-identity/document | yq .region",
                    )
                datacenterResponse.text.trim()
            } else {
                ""
            }

        val cassandraHost = tfstate.getHosts(ServerType.Cassandra).first().private

        tfstate.withHosts(ServerType.Stress, hosts, parallel = true) {
            setup(it)
            setupStressSystemdEnv(it, cassandraHost, datacenter)
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
