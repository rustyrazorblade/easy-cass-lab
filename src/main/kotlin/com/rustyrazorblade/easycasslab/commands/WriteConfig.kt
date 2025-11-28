package com.rustyrazorblade.easycasslab.commands

import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.annotations.RequireProfileSetup
import com.rustyrazorblade.easycasslab.configuration.ClusterStateManager
import com.rustyrazorblade.easycasslab.configuration.ServerType
import com.rustyrazorblade.easycasslab.di.TFStateProvider
import com.rustyrazorblade.easycasslab.output.OutputHandler
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.io.File

/**
 * Write a new cassandra configuration patch file.
 */
@RequireProfileSetup
@Command(
    name = "write-config",
    aliases = ["wc"],
    description = ["Write a new cassandra configuration patch file"],
)
class WriteConfig(
    val context: Context,
) : PicoCommand,
    KoinComponent {
    private val outputHandler: OutputHandler by inject()
    private val tfStateProvider: TFStateProvider by inject()
    private val clusterStateManager: ClusterStateManager by inject()
    private val tfstate by lazy { tfStateProvider.getDefault() }

    companion object {
        private const val DEFAULT_TOKEN_COUNT = 4
        private const val DEFAULT_CONCURRENT_READS = 64
        private const val DEFAULT_CONCURRENT_WRITES = 64
    }

    @Parameters(description = ["Patch file name"], defaultValue = "cassandra.patch.yaml")
    var file: String = "cassandra.patch.yaml"

    @Option(names = ["-t", "--tokens"], description = ["Number of tokens"])
    var tokens: Int = DEFAULT_TOKEN_COUNT

    override fun execute() {
        // create the cassandra.yaml patch file
        outputHandler.handleMessage("Writing new configuration file to $file.")
        outputHandler.handleMessage(
            "It can be applied to the lab via easy-cass-lab update-config " +
                "(or automatically when calling use-cassandra)",
        )

        val state = clusterStateManager.load()

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
                                    tfstate
                                        .getHosts(ServerType.Cassandra)
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
