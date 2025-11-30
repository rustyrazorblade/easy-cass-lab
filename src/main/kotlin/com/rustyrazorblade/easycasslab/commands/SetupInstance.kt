package com.rustyrazorblade.easycasslab.commands

import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.annotations.RequireProfileSetup
import com.rustyrazorblade.easycasslab.commands.mixins.HostsMixin
import com.rustyrazorblade.easycasslab.configuration.Host
import com.rustyrazorblade.easycasslab.configuration.ServerType
import com.rustyrazorblade.easycasslab.configuration.getHosts
import com.rustyrazorblade.easycasslab.configuration.toHost
import com.rustyrazorblade.easycasslab.services.HostOperationsService
import org.koin.core.component.inject
import picocli.CommandLine.Command
import picocli.CommandLine.Mixin
import java.nio.file.Path

/**
 * Runs setup_instance.sh on all Cassandra instances.
 */
@RequireProfileSetup
@Command(
    name = "setup-instances",
    aliases = ["si"],
    description = ["Runs setup_instance.sh on all Cassandra instances"],
)
class SetupInstance(
    context: Context,
) : PicoBaseCommand(context) {
    private val hostOperationsService: HostOperationsService by inject()

    @Mixin
    var hosts = HostsMixin()

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
        val stressHosts = clusterState.getHosts(ServerType.Stress)
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

        val cassandraHost = clusterState.getHosts(ServerType.Cassandra).first().private

        hostOperationsService.withHosts(clusterState.hosts, ServerType.Stress, hosts.hostList, parallel = true) { host ->
            val h = host.toHost()
            setup(h)
            setupStressSystemdEnv(h, cassandraHost, datacenter)
            remoteOps.executeRemotely(h, "sudo hostnamectl set-hostname ${h.alias}").text
            remoteOps.upload(h, Path.of("setup_instance.sh"), "setup_instance.sh")
            remoteOps.executeRemotely(h, "sudo bash setup_instance.sh").text
        }
        hostOperationsService.withHosts(clusterState.hosts, ServerType.Cassandra, "") { host ->
            val h = host.toHost()
            setup(h)
            remoteOps.executeRemotely(h, "sudo hostnamectl set-hostname ${h.alias}").text
            remoteOps.upload(h, Path.of("setup_instance.sh"), "setup_instance.sh")
            remoteOps.executeRemotely(h, "sudo bash setup_instance.sh").text
        }
        hostOperationsService.withHosts(clusterState.hosts, ServerType.Control, "") { host ->
            // Control nodes need minimal setup - just hostname configuration
            // K3s installation will happen later in startK3sOnAllNodes()
            remoteOps.executeRemotely(host.toHost(), "sudo hostnamectl set-hostname ${host.alias}").text
        }
    }
}
