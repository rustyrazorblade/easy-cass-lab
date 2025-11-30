package com.rustyrazorblade.easycasslab.commands

import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.annotations.RequireProfileSetup
import com.rustyrazorblade.easycasslab.annotations.RequireSSHKey
import com.rustyrazorblade.easycasslab.commands.mixins.HostsMixin
import com.rustyrazorblade.easycasslab.configuration.ServerType
import com.rustyrazorblade.easycasslab.configuration.toHost
import com.rustyrazorblade.easycasslab.services.CassandraService
import com.rustyrazorblade.easycasslab.services.EasyStressService
import com.rustyrazorblade.easycasslab.services.HostOperationsService
import com.rustyrazorblade.easycasslab.services.SidecarService
import org.koin.core.component.inject
import picocli.CommandLine.Command
import picocli.CommandLine.Mixin

// @McpCommand - disabled for now like the original

/**
 * Stop cassandra on all nodes via service command.
 */
@RequireProfileSetup
@RequireSSHKey
@Command(
    name = "stop",
    description = ["Stop cassandra on all nodes via service command"],
)
class Stop(
    context: Context,
) : PicoBaseCommand(context) {
    private val cassandraService: CassandraService by inject()
    private val easyStressService: EasyStressService by inject()
    private val sidecarService: SidecarService by inject()
    private val hostOperationsService: HostOperationsService by inject()

    @Mixin
    var hosts = HostsMixin()

    override fun execute() {
        outputHandler.handleMessage("Stopping cassandra service on all nodes.")

        hostOperationsService.withHosts(clusterState.hosts, ServerType.Cassandra, hosts.hostList) { host ->
            cassandraService.stop(host.toHost()).getOrThrow()
        }

        stopSidecar()
        stopCassandraEasyStress()
    }

    /**
     * Stop cassandra-sidecar service on Cassandra nodes
     */
    private fun stopSidecar() {
        outputHandler.handleMessage("Stopping cassandra-sidecar on Cassandra nodes...")

        hostOperationsService.withHosts(clusterState.hosts, ServerType.Cassandra, hosts.hostList, parallel = true) { host ->
            sidecarService
                .stop(host.toHost())
                .onFailure { e ->
                    outputHandler.handleMessage("Warning: Failed to stop cassandra-sidecar on ${host.alias}: ${e.message}")
                }
        }

        outputHandler.handleMessage("cassandra-sidecar shutdown completed on Cassandra nodes")
    }

    /**
     * Stop cassandra-easy-stress service on stress nodes
     */
    private fun stopCassandraEasyStress() {
        outputHandler.handleMessage("Stopping cassandra-easy-stress on stress nodes...")

        hostOperationsService.withHosts(clusterState.hosts, ServerType.Stress, hosts.hostList, parallel = true) { host ->
            easyStressService
                .stop(host.toHost())
                .onFailure { e ->
                    outputHandler.handleMessage("Warning: Failed to stop cassandra-easy-stress on ${host.alias}: ${e.message}")
                }
        }

        outputHandler.handleMessage("cassandra-easy-stress shutdown completed on stress nodes")
    }
}
