package com.rustyrazorblade.easycasslab.commands

import com.github.ajalt.mordant.TermColors
import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.annotations.McpCommand
import com.rustyrazorblade.easycasslab.annotations.RequireDocker
import com.rustyrazorblade.easycasslab.annotations.RequireProfileSetup
import com.rustyrazorblade.easycasslab.annotations.RequireSSHKey
import com.rustyrazorblade.easycasslab.commands.mixins.HostsMixin
import com.rustyrazorblade.easycasslab.configuration.ServerType
import com.rustyrazorblade.easycasslab.configuration.User
import com.rustyrazorblade.easycasslab.configuration.toHost
import com.rustyrazorblade.easycasslab.services.CassandraService
import com.rustyrazorblade.easycasslab.services.EasyStressService
import com.rustyrazorblade.easycasslab.services.HostOperationsService
import com.rustyrazorblade.easycasslab.services.SidecarService
import org.koin.core.component.inject
import picocli.CommandLine.Command
import picocli.CommandLine.Mixin
import picocli.CommandLine.Option
import java.io.File

/**
 * Start cassandra on all nodes via service command.
 */
@McpCommand
@RequireDocker
@RequireProfileSetup
@RequireSSHKey
@Command(
    name = "start",
    description = ["Start cassandra on all nodes via service command"],
)
class Start(
    context: Context,
) : PicoBaseCommand(context) {
    private val userConfig: User by inject()
    private val cassandraService: CassandraService by inject()
    private val easyStressService: EasyStressService by inject()
    private val sidecarService: SidecarService by inject()
    private val hostOperationsService: HostOperationsService by inject()

    companion object {
        private const val DEFAULT_SLEEP_BETWEEN_STARTS_SECONDS = 120L
    }

    @Option(names = ["--sleep"], description = ["Time to sleep between starts in seconds"])
    var sleep: Long = DEFAULT_SLEEP_BETWEEN_STARTS_SECONDS

    @Mixin
    var hosts = HostsMixin()

    override fun execute() {
        with(TermColors()) {
            hostOperationsService.withHosts(clusterState.hosts, ServerType.Cassandra, hosts.hostList) { host ->
                val h = host.toHost()
                outputHandler.handleMessage(green("Starting $h"))
                // start() defaults to wait=true, which includes waiting for UP/NORMAL
                cassandraService.start(h).getOrThrow()
            }

            // Start cassandra-sidecar on Cassandra nodes
            hostOperationsService.withHosts(clusterState.hosts, ServerType.Cassandra, hosts.hostList, parallel = true) { host ->
                sidecarService
                    .start(host.toHost())
                    .onFailure { e ->
                        outputHandler.handleMessage("Warning: Failed to start cassandra-sidecar on ${host.alias}: ${e.message}")
                    }
            }
        }

        // Start cassandra-easy-stress on stress nodes
        startCassandraEasyStress()

        // Start axon-agent on Cassandra nodes if configured
        if (userConfig.axonOpsOrg.isNotBlank() && userConfig.axonOpsKey.isNotBlank()) {
            outputHandler.handleMessage("Starting axon-agent on Cassandra nodes...")
            hostOperationsService.withHosts(clusterState.hosts, ServerType.Cassandra, "", parallel = true) { host ->
                remoteOps.executeRemotely(host.toHost(), "sudo systemctl start axon-agent")
            }
        }

        // Inform user about AxonOps Workbench configuration if it exists
        val axonOpsWorkbenchFile = File("axonops-workbench.json")
        if (axonOpsWorkbenchFile.exists()) {
            outputHandler.handleMessage("")
            outputHandler.handleMessage("AxonOps Workbench configuration available:")
            outputHandler.handleMessage("To import into AxonOps Workbench, run:")
            outputHandler.handleMessage(
                "  /path/to/axonops-workbench -v --import-workspace=axonops-workbench.json",
            )
            outputHandler.handleMessage("")
        }
    }

    /**
     * Start cassandra-easy-stress service on stress nodes
     */
    private fun startCassandraEasyStress() {
        outputHandler.handleMessage("Starting cassandra-easy-stress on stress nodes...")

        hostOperationsService.withHosts(clusterState.hosts, ServerType.Stress, hosts.hostList, parallel = true) { host ->
            easyStressService
                .start(host.toHost())
                .onFailure { e ->
                    outputHandler.handleMessage("Warning: Failed to start cassandra-easy-stress on ${host.alias}: ${e.message}")
                }
        }

        outputHandler.handleMessage("cassandra-easy-stress startup completed on stress nodes")
    }
}
