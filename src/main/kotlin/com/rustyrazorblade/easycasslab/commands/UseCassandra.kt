package com.rustyrazorblade.easycasslab.commands

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import com.beust.jcommander.ParametersDelegate
import com.fasterxml.jackson.annotation.JsonIgnore
import com.github.ajalt.mordant.TermColors
import  com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.commands.delegates.Hosts
import  com.rustyrazorblade.easycasslab.configuration.*
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.FileNotFoundException
import kotlin.system.exitProcess

@Parameters(commandDescription = "Use a Cassandra version (3.0, 3.11, 4.0, 4.1)")
class UseCassandra(@JsonIgnore val context: Context) : ICommand {
    @Parameter
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
            context.tfstate
        } catch (e: FileNotFoundException) {
            println("Error: terraform config file not found.  Please run easy-cass-lab up first to establish IP addresses for seed listing.")
            exitProcess(1)
        }

        val cassandraHosts = context.tfstate.getHosts(ServerType.Cassandra)
        println("Using version ${version} on ${cassandraHosts.size} hosts, filter: $hosts")

        context.tfstate.withHosts(ServerType.Cassandra, hosts) {
            if (javaVersion.isNotBlank()) {
                context.executeRemotely(it, "set-java-version ${javaVersion} ${version}")
            }
            context.executeRemotely(it, "sudo use-cassandra ${version}").text
            state.versions?.put(it.alias, version)
        }

        state.save()

        DownloadConfig(context).execute()

        // make sure we only apply to the filtered hosts
        val uc = UpdateConfig(context)
        uc.hosts = hosts
        uc.execute()

        with (TermColors()) {
            println("You can update ${green("cassandra.patch.yaml")} and the JVM config files under ${green(version)}, " +
                    "then run ${green("easy-cass-lab update-config")} to apply the changes.")
        }
    }
}