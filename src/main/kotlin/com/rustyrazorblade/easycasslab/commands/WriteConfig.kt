package com.rustyrazorblade.easycasslab.commands

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.configuration.ServerType
import java.io.File


@Parameters(commandDescription = "Write a new cassandra configuration patch file")
class WriteConfig(val context: Context) : ICommand {
    @Parameter(description = "Patch file name")
    var file: String = "cassandra.patch.yaml"

    @Parameter(names = ["-t", "--tokens"])
    var tokens: Int = 4

    override fun execute() {
        println("Writing new configuration file to $file.") // create the cassandra.yaml patch file
        println("It can be applied to the lab via easy-cass-lab update-config (or automatically when calling use-cassandra)")

        val data = object {
            val cluster_name = "Test Cluster" // TODO: put actual cluster name here
            val num_tokens = tokens
            val seed_provider = object {
                val class_name = "org.apache.cassandra.locator.SimpleSeedProvider"
                val parameters = object {
                    val seeds = context.tfstate.getHosts(ServerType.Cassandra).map{ it.private }.joinToString(",")
                }
            }
            val hints_directory = "/mnt/cassandra/hints"
            val data_file_directories = listOf("/mnt/cassandra/data")
            val commitlog_directory = "/mnt/cassandra/commitlog"
            val concurrent_reads = 64
            val concurrent_writes = 64
            val trickle_fsync = true
            val endpoint_snitch = "Ec2Snitch"
        }

        context.yaml.writeValue(File("cassandra.patch.yaml"), data)
    }

}