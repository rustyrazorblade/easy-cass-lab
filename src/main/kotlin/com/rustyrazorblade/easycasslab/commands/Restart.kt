package com.rustyrazorblade.easycasslab.commands

import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.annotations.McpCommand
import com.rustyrazorblade.easycasslab.annotations.RequireProfileSetup
import com.rustyrazorblade.easycasslab.commands.mixins.HostsMixin
import com.rustyrazorblade.easycasslab.configuration.ServerType
import com.rustyrazorblade.easycasslab.services.CassandraService
import com.rustyrazorblade.easycasslab.services.EasyStressService
import com.rustyrazorblade.easycasslab.services.SidecarService
import org.koin.core.component.inject
import picocli.CommandLine.Command
import picocli.CommandLine.Mixin

/**
 * Restart cassandra on all nodes.
 */
@McpCommand
@RequireProfileSetup
@Command(
    name = "restart",
    description = ["Restart cassandra"],
)
class Restart(
    context: Context,
) : PicoBaseCommand(context) {
    private val cassandraService: CassandraService by inject()
    private val easyStressService: EasyStressService by inject()
    private val sidecarService: SidecarService by inject()

    @Mixin
    var hosts = HostsMixin()

    override fun execute() {
        outputHandler.handleMessage("Restarting cassandra service on all nodes.")

        tfstate.withHosts(ServerType.Cassandra, hosts) {
            cassandraService.restart(it).getOrThrow()
        }

        restartSidecar()
        restartCassandraEasyStress()
    }

    /**
     * Restart cassandra-sidecar service on Cassandra nodes
     */
    private fun restartSidecar() {
        outputHandler.handleMessage("Restarting cassandra-sidecar on Cassandra nodes...")

        tfstate.withHosts(ServerType.Cassandra, hosts, parallel = true) { host ->
            sidecarService
                .restart(host)
                .onFailure { e ->
                    outputHandler.handleMessage("Warning: Failed to restart cassandra-sidecar on ${host.alias}: ${e.message}")
                }
        }

        outputHandler.handleMessage("cassandra-sidecar restart completed on Cassandra nodes")
    }

    /**
     * Restart cassandra-easy-stress service on stress nodes
     */
    private fun restartCassandraEasyStress() {
        outputHandler.handleMessage("Restarting cassandra-easy-stress on stress nodes...")

        tfstate.withHosts(ServerType.Stress, hosts, parallel = true) { host ->
            easyStressService
                .restart(host)
                .onFailure { e ->
                    outputHandler.handleMessage("Warning: Failed to restart cassandra-easy-stress on ${host.alias}: ${e.message}")
                }
        }

        outputHandler.handleMessage("cassandra-easy-stress restart completed on stress nodes")
    }
}
