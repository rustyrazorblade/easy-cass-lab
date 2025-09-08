package com.rustyrazorblade.easycasslab.commands

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import com.fasterxml.jackson.annotation.JsonIgnore
import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.configuration.ClusterState
import com.rustyrazorblade.easycasslab.configuration.ServerType
import com.rustyrazorblade.easycasslab.di.TFStateProvider
import com.rustyrazorblade.easycasslab.output.OutputHandler
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File

@Parameters(commandDescription = "Write a new cassandra configuration patch file")
class WriteConfig : ICommand, KoinComponent {
    private val context: Context by inject()
    private val outputHandler: OutputHandler by inject()
    private val tfStateProvider: TFStateProvider by inject()
    private val tfstate by lazy { tfStateProvider.getDefault() }

    companion object {
        private const val DEFAULT_TOKEN_COUNT = 4
        private const val DEFAULT_CONCURRENT_READS = 64
        private const val DEFAULT_CONCURRENT_WRITES = 64
    }

    @Parameter(description = "Patch file name")
    var file: String = "cassandra.patch.yaml"

    @Parameter(names = ["-t", "--tokens"])
    var tokens: Int = DEFAULT_TOKEN_COUNT

    override fun execute() {
        // create the cassandra.yaml patch file
        outputHandler.handleMessage("Writing new configuration file to $file.")
        outputHandler.handleMessage(
            "It can be applied to the lab via easy-cass-lab update-config " +
                "(or automatically when calling use-cassandra)",
        )

        val state = ClusterState.load()

        val data =
            object {
                val cluster_name = state.name
                val num_tokens = tokens
                val seed_provider =
                    object {
                        val class_name = "org.apache.cassandra.locator.SimpleSeedProvider"
                        val parameters =
                            object {
                                val seeds =
                                    tfstate.getHosts(ServerType.Cassandra)
                                        .map { it.private }
                                        .take(1)
                                        .joinToString(",")
                            }
                    }
                val hints_directory = "/mnt/cassandra/hints"
                val data_file_directories = listOf("/mnt/cassandra/data")
                val commitlog_directory = "/mnt/cassandra/commitlog"
                val concurrent_reads = DEFAULT_CONCURRENT_READS
                val concurrent_writes = DEFAULT_CONCURRENT_WRITES
                val trickle_fsync = true
                val endpoint_snitch = "Ec2Snitch"
            }

        context.yaml.writeValue(File("cassandra.patch.yaml"), data)
    }
}
