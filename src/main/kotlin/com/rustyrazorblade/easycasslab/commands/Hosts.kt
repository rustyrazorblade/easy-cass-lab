package com.rustyrazorblade.easycasslab.commands

import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.annotations.McpCommand
import com.rustyrazorblade.easycasslab.annotations.RequireProfileSetup
import com.rustyrazorblade.easycasslab.configuration.ClusterStateManager
import com.rustyrazorblade.easycasslab.configuration.Host
import com.rustyrazorblade.easycasslab.configuration.ServerType
import com.rustyrazorblade.easycasslab.configuration.getHosts
import com.rustyrazorblade.easycasslab.output.OutputHandler
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import picocli.CommandLine.Command
import picocli.CommandLine.Option

/**
 * Lists all hosts in the cluster.
 */
@McpCommand
@RequireProfileSetup
@Command(
    name = "hosts",
    description = ["List all hosts in the cluster"],
)
class Hosts(
    private val context: Context,
) : PicoCommand,
    KoinComponent {
    private val outputHandler: OutputHandler by inject()
    private val clusterStateManager: ClusterStateManager by inject()
    private val clusterState by lazy { clusterStateManager.load() }

    @Option(names = ["-c"], description = ["Show Cassandra as a comma delimited list"])
    var cassandra: Boolean = false

    data class HostOutput(
        val cassandra: List<Host>,
        val stress: List<Host>,
        val control: List<Host>,
    )

    override fun execute() {
        if (!clusterStateManager.exists()) {
            outputHandler.handleMessage(
                "Cluster state does not exist yet, most likely easy-cass-lab up has not been run.",
            )
            return
        }

        val output =
            with(clusterState) {
                HostOutput(
                    getHosts(ServerType.Cassandra),
                    getHosts(ServerType.Stress),
                    getHosts(ServerType.Control),
                )
            }

        if (cassandra) {
            val hosts = clusterState.getHosts(ServerType.Cassandra)
            val csv = hosts.map { it.public }.joinToString(",")
            outputHandler.handleMessage(csv)
        } else {
            context.yaml.writeValue(System.out, output)
        }
    }
}
