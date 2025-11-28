package com.rustyrazorblade.easycasslab.commands

import com.fasterxml.jackson.annotation.JsonIgnore
import com.github.ajalt.mordant.TermColors
import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.annotations.McpCommand
import com.rustyrazorblade.easycasslab.annotations.RequireProfileSetup
import com.rustyrazorblade.easycasslab.commands.mixins.HostsMixin
import com.rustyrazorblade.easycasslab.configuration.ClusterStateManager
import com.rustyrazorblade.easycasslab.configuration.ServerType
import io.github.oshai.kotlinlogging.KotlinLogging
import org.koin.core.component.inject
import picocli.CommandLine.Command
import picocli.CommandLine.Mixin
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.io.FileNotFoundException
import kotlin.system.exitProcess

/**
 * Use a Cassandra version (3.0, 3.11, 4.0, 4.1).
 */
@McpCommand
@RequireProfileSetup
@Command(
    name = "use",
    description = ["Use a Cassandra version (3.0, 3.11, 4.0, 4.1)"],
)
class UseCassandra(
    context: Context,
) : PicoBaseCommand(context) {
    private val clusterStateManager: ClusterStateManager by inject()

    @Parameters(description = ["Cassandra version"], index = "0")
    lateinit var version: String

    @Mixin
    var hosts = HostsMixin()

    @JsonIgnore val log = KotlinLogging.logger {}

    @Option(
        names = ["--java", "-j"],
        description = ["Java Version Override, 8, 11 or 17 accepted"],
    )
    var javaVersion = ""

    @Suppress("TooGenericExceptionCaught")
    override fun execute() {
        check(version.isNotBlank())
        val state = clusterStateManager.load()
        try {
            tfstate
        } catch (ignored: FileNotFoundException) {
            outputHandler.handleMessage(
                "Error: terraform config file not found.  Please run easy-cass-lab up first to " +
                    "establish IP addresses for seed listing.",
            )
            exitProcess(1)
        }

        val cassandraHosts = tfstate.getHosts(ServerType.Cassandra)
        outputHandler.handleMessage(
            "Using version $version on ${cassandraHosts.size} hosts, filter: $hosts",
        )

        tfstate.withHosts(ServerType.Cassandra, hosts, parallel = true) {
            if (javaVersion.isNotBlank()) {
                remoteOps.executeRemotely(it, "set-java-version $javaVersion $version")
            }
            remoteOps.executeRemotely(it, "sudo use-cassandra $version").text
            state.versions?.put(it.alias, version)
        }

        clusterStateManager.save(state)

        DownloadConfig(context).execute()

        // make sure we only apply to the filtered hosts
        val uc = UpdateConfig(context)
        uc.hosts = hosts
        uc.execute()

        with(TermColors()) {
            outputHandler.handleMessage(
                "You can update ${green("cassandra.patch.yaml")} and the JVM config files under ${green(version)}, " +
                    "then run ${green("easy-cass-lab update-config")} to apply the changes.",
            )
        }
    }
}
