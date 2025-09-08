package com.rustyrazorblade.easycasslab.commands

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import com.beust.jcommander.ParametersDelegate
import com.fasterxml.jackson.annotation.JsonIgnore
import com.github.ajalt.mordant.TermColors
import com.rustyrazorblade.easycasslab.annotations.McpCommand
import com.rustyrazorblade.easycasslab.commands.delegates.Hosts
import com.rustyrazorblade.easycasslab.configuration.ClusterState
import com.rustyrazorblade.easycasslab.configuration.ServerType
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.FileNotFoundException
import kotlin.system.exitProcess

@McpCommand
@Parameters(commandDescription = "Use a Cassandra version (3.0, 3.11, 4.0, 4.1)")
class UseCassandra : BaseCommand() {
    @Parameter(description = "Cassandra version", required = true)
    var version: String = ""

    @ParametersDelegate
    var hosts = Hosts()

    @JsonIgnore
    val log = KotlinLogging.logger {}

    @Parameter(names = ["--java", "-j"], description = "Java Version Override, 8, 11 or 17 accepted")
    var javaVersion = ""

//    @Parameter(names = ["--bti"], description = "Enable BTI Storage")
//    var bti = false

    override fun execute() {
        check(version.isNotBlank())
        val state = ClusterState.load()
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
        outputHandler.handleMessage("Using version $version on ${cassandraHosts.size} hosts, filter: $hosts")

        tfstate.withHosts(ServerType.Cassandra, hosts, parallel = true) {
            if (javaVersion.isNotBlank()) {
                remoteOps.executeRemotely(it, "set-java-version $javaVersion $version")
            }
            remoteOps.executeRemotely(it, "sudo use-cassandra $version").text
            state.versions?.put(it.alias, version)
        }

        state.save()

        DownloadConfig().execute()

        // make sure we only apply to the filtered hosts
        val uc = UpdateConfig()
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
