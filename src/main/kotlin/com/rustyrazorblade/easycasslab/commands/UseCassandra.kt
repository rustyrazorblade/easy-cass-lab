package com.rustyrazorblade.easycasslab.commands

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import com.beust.jcommander.ParametersDelegate
import com.fasterxml.jackson.annotation.JsonIgnore
import com.github.ajalt.mordant.TermColors
import  com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.commands.delegates.Hosts
import  com.rustyrazorblade.easycasslab.configuration.*
import org.apache.logging.log4j.kotlin.logger
import java.io.FileNotFoundException

@Parameters(commandDescription = "Use a Cassandra version (3.0, 3.11, 4.0, 4.1)")
class UseCassandra(@JsonIgnore val context: Context) : ICommand {
    @Parameter
    var version: String = ""

    @ParametersDelegate
    var hosts = Hosts()

    @JsonIgnore
    val log = logger()

    @Parameter(names = ["--java", "-j"], description = "Java Version Override, 8, 11 or 17 accepted")
    var javaVersion = ""

    override fun execute() {
        check(version.isNotBlank())
        val state = ClusterState.load()
        try {
            context.tfstate
        } catch (e: FileNotFoundException) {
            println("Error: terraform config file not found.  Please run easy-cass-lab up first to establish IP addresses for seed listing.")
            System.exit(1)
        }

        val cassandraHosts = context.tfstate.getHosts(ServerType.Cassandra)
        println("Using version ${version} on ${cassandraHosts.size} hosts, filter: $hosts")

        // optionally include the java version if specified
        val javaString = if (javaVersion.isNotBlank()) " -j $javaVersion " else ""

        context.tfstate.withHosts(ServerType.Cassandra, hosts.hosts) {
            context.executeRemotely(it, "sudo use-cassandra $javaString ${version}")
            state.versions?.put(it.alias, version)
        }

        state.save()

        DownloadConfig(context).execute()

        // make sure we only apply to the filtered hosts
        val uc = UpdateConfig(context)
        uc.hosts = hosts
        uc.execute()

        with (TermColors()) {
            println("You can update the ${green("cassandra.patch.yaml")} and  ${green("jvm.options")} files " +
                    "then run ${green("easy-cass-lab update-config")} to apply the changes.")
        }
    }
}