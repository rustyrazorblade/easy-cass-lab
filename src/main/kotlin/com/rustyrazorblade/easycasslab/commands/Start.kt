package com.rustyrazorblade.easycasslab.commands

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import com.beust.jcommander.ParametersDelegate
import com.github.ajalt.mordant.TermColors
import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.annotations.McpCommand
import com.rustyrazorblade.easycasslab.annotations.RequireDocker
import com.rustyrazorblade.easycasslab.annotations.RequireProfileSetup
import com.rustyrazorblade.easycasslab.annotations.RequireSSHKey
import com.rustyrazorblade.easycasslab.commands.delegates.Hosts
import com.rustyrazorblade.easycasslab.configuration.ServerType
import com.rustyrazorblade.easycasslab.configuration.User
import com.rustyrazorblade.easycasslab.services.CassandraService
import com.rustyrazorblade.easycasslab.services.EasyStressService
import com.rustyrazorblade.easycasslab.services.SidecarService
import org.koin.core.component.inject
import java.io.File

@McpCommand
@RequireDocker
@RequireProfileSetup
@RequireSSHKey
@Parameters(commandDescription = "Start cassandra on all nodes via service command")
class Start(
    context: Context,
) : BaseCommand(context) {
    private val userConfig: User by inject()
    private val cassandraService: CassandraService by inject()
    private val easyStressService: EasyStressService by inject()
    private val sidecarService: SidecarService by inject()

    companion object {
        private const val DEFAULT_SLEEP_BETWEEN_STARTS_SECONDS = 120L
    }

    @Parameter(names = ["--sleep"], description = "Time to sleep between starts in seconds")
    var sleep: Long = DEFAULT_SLEEP_BETWEEN_STARTS_SECONDS

    @ParametersDelegate var hosts = Hosts()

    override fun execute() {
        with(TermColors()) {
            tfstate.withHosts(ServerType.Cassandra, hosts) {
                outputHandler.handleMessage(green("Starting $it"))
                // start() defaults to wait=true, which includes waiting for UP/NORMAL
                cassandraService.start(it).getOrThrow()
            }

            // Start cassandra-sidecar on Cassandra nodes
            tfstate.withHosts(ServerType.Cassandra, hosts, parallel = true) { host ->
                sidecarService
                    .start(host)
                    .onFailure { e ->
                        outputHandler.handleMessage("Warning: Failed to start cassandra-sidecar on ${host.alias}: ${e.message}")
                    }
            }
        }

        // Start cassandra-easy-stress on stress nodes
        startCassandraEasyStress()

        if (userConfig.axonOpsOrg.isNotBlank() && userConfig.axonOpsKey.isNotBlank()) {
            StartAxonOps(context).execute()
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

        tfstate.withHosts(ServerType.Stress, hosts, parallel = true) { host ->
            easyStressService
                .start(host)
                .onFailure { e ->
                    outputHandler.handleMessage("Warning: Failed to start cassandra-easy-stress on ${host.alias}: ${e.message}")
                }
        }

        outputHandler.handleMessage("cassandra-easy-stress startup completed on stress nodes")
    }
}
