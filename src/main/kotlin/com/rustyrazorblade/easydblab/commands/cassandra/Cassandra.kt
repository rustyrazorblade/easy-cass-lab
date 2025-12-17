package com.rustyrazorblade.easydblab.commands.cassandra

import com.rustyrazorblade.easydblab.commands.cassandra.stress.Stress
import picocli.CommandLine.Command
import picocli.CommandLine.Model.CommandSpec
import picocli.CommandLine.Spec

/**
 * Parent command for Cassandra-related operations.
 *
 * This command groups subcommands for Cassandra cluster management and tooling including:
 * - Cluster lifecycle: down, start, stop, restart
 * - Configuration: use, list, download-config, write-config, update-config
 * - Stress testing: stress (with nested subcommands)
 */
@Command(
    name = "cassandra",
    description = ["Cassandra cluster management and tooling operations"],
    mixinStandardHelpOptions = true,
    subcommands = [
        Down::class,
        DownloadConfig::class,
        ListVersions::class,
        Restart::class,
        Start::class,
        Stop::class,
        UpdateConfig::class,
        UseCassandra::class,
        WriteConfig::class,
        Stress::class,
    ],
)
class Cassandra : Runnable {
    @Spec
    lateinit var spec: CommandSpec

    override fun run() {
        spec.commandLine().usage(System.out)
    }
}
