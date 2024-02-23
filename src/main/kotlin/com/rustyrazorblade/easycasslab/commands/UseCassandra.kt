package com.rustyrazorblade.easycasslab.commands

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import com.github.ajalt.mordant.TermColors
import  com.rustyrazorblade.easycasslab.Context
import  com.rustyrazorblade.easycasslab.core.YamlDelegate
import  com.rustyrazorblade.easycasslab.configuration.*
import  com.rustyrazorblade.easycasslab.containers.CassandraUnpack
import org.apache.commons.io.FileUtils
import org.apache.logging.log4j.kotlin.logger
import org.apache.sshd.client.SshClient
import org.apache.sshd.common.keyprovider.KeyIdentityProvider
import org.apache.sshd.common.util.security.SecurityUtils
import java.io.File
import java.io.FileFilter
import java.io.FileNotFoundException
import java.time.Duration
import java.util.*
import kotlin.io.path.Path

@Parameters(commandDescription = "Use a Cassandra version (3.0, 3.11, 4.0, 4.1)")
class UseCassandra(val context: Context) : ICommand {
    @Parameter
    var version: String = ""

    val log = logger()

//    @Parameter(description = "Configuration settings to change in the cassandra.yaml file specified in the format key:value,...", names = ["--config", "-c"])
//    var configSettings = listOf<String>()

    val yaml by YamlDelegate()

    override fun execute() {
        check(version.isNotBlank())
        try {
            context.tfstate
        } catch (e: FileNotFoundException) {
            println("Error: terraform config file not found.  Please run easy-cass-lab up first to establish IP addresses for seed listing.")
            System.exit(1)
        }

        val cassandraHosts = context.tfstate.getHosts(ServerType.Cassandra)
        println("Using version ${version} on ${cassandraHosts.size} hosts")
        cassandraHosts.map { host ->
            context.executeRemotely(host, "sudo use-cassandra ${version}")
        }

        UpdateConfig(context).execute()

    }
}