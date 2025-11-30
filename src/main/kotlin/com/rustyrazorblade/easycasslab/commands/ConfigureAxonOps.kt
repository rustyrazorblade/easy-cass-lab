package com.rustyrazorblade.easycasslab.commands

import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.annotations.RequireProfileSetup
import com.rustyrazorblade.easycasslab.annotations.RequireSSHKey
import com.rustyrazorblade.easycasslab.commands.mixins.HostsMixin
import com.rustyrazorblade.easycasslab.configuration.ServerType
import com.rustyrazorblade.easycasslab.configuration.User
import com.rustyrazorblade.easycasslab.configuration.toHost
import com.rustyrazorblade.easycasslab.services.HostOperationsService
import org.koin.core.component.inject
import picocli.CommandLine.Command
import picocli.CommandLine.Mixin
import picocli.CommandLine.Option
import kotlin.system.exitProcess

/**
 * Setup / configure axon-agent for use with the Cassandra cluster.
 */
@RequireProfileSetup
@RequireSSHKey
@Command(
    name = "configure-axonops",
    description = ["Setup / configure axon-agent for use with the Cassandra cluster"],
)
class ConfigureAxonOps(
    context: Context,
) : PicoBaseCommand(context) {
    private val userConfig: User by inject()
    private val hostOperationsService: HostOperationsService by inject()

    @Option(names = ["--org"], description = ["AxonOps Organization Name"])
    var org = ""

    @Option(names = ["--key"], description = ["AxonOps API Key"])
    var key = ""

    @Mixin
    var hosts = HostsMixin()

    override fun execute() {
        val axonOrg = if (org.isNotBlank()) org else userConfig.axonOpsOrg
        val axonKey = if (key.isNotBlank()) key else userConfig.axonOpsKey
        if ((axonOrg.isBlank() || axonKey.isBlank())) {
            outputHandler.handleMessage("--org and --key are required")
            exitProcess(1)
        }

        hostOperationsService.withHosts(clusterState.hosts, ServerType.Cassandra, hosts.hostList) { host ->
            val it = host.toHost()
            outputHandler.handleMessage("Configure axonops on $it")

            remoteOps
                .executeRemotely(
                    it,
                    "/usr/local/bin/setup-axonops $axonOrg $axonKey",
                    secret = true,
                ).text
        }
    }
}
