package com.rustyrazorblade.easycasslab.commands

import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.annotations.RequireProfileSetup
import com.rustyrazorblade.easycasslab.annotations.RequireSSHKey
import com.rustyrazorblade.easycasslab.commands.mixins.HostsMixin
import com.rustyrazorblade.easycasslab.configuration.ServerType
import com.rustyrazorblade.easycasslab.services.CassandraService
import com.rustyrazorblade.easycasslab.services.EasyStressService
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

    @Mixin
    var hosts = HostsMixin()

    override fun execute() {
        outputHandler.handleMessage("Stopping cassandra service on all nodes.")

        tfstate.withHosts(ServerType.Cassandra, hosts) {
            cassandraService.stop(it).getOrThrow()
        }

        stopSidecar()
        stopCassandraEasyStress()
    }

    /**
     * Stop cassandra-sidecar service on Cassandra nodes
     */
    private fun stopSidecar() {
        outputHandler.handleMessage("Stopping cassandra-sidecar on Cassandra nodes...")

        tfstate.withHosts(ServerType.Cassandra, hosts, parallel = true) { host ->
            sidecarService
                .stop(host)
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

        tfstate.withHosts(ServerType.Stress, hosts, parallel = true) { host ->
            easyStressService
                .stop(host)
                .onFailure { e ->
                    outputHandler.handleMessage("Warning: Failed to stop cassandra-easy-stress on ${host.alias}: ${e.message}")
                }
        }

        outputHandler.handleMessage("cassandra-easy-stress shutdown completed on stress nodes")
    }
}
