package com.rustyrazorblade.easycasslab.commands

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import com.fasterxml.jackson.annotation.JsonIgnore
import com.github.ajalt.mordant.TermColors
import  com.rustyrazorblade.easycasslab.Context
import  com.rustyrazorblade.easycasslab.configuration.*
import org.apache.logging.log4j.kotlin.logger
import java.io.FileNotFoundException

@Parameters(commandDescription = "Use a Cassandra version (3.0, 3.11, 4.0, 4.1)")
class UseCassandra(@JsonIgnore val context: Context) : ICommand {
    @Parameter
    var version: String = ""

    @Parameter(description = "Hosts to run this on, leave blank for all hosts.", names = ["--hosts"])
    var hosts = ""

    @JsonIgnore
    val log = logger()

//    @Parameter(description = "Configuration settings to change in the cassandra.yaml file specified in the format key:value,...", names = ["--config", "-c"])
//    var configSettings = listOf<String>()


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

        // save the cluster state

        context.tfstate.withHosts(ServerType.Cassandra, hosts) {
            context.executeRemotely(it, "sudo use-cassandra ${version}")
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