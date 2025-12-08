package com.rustyrazorblade.easydblab.commands.cassandra

import picocli.CommandLine.Command
import picocli.CommandLine.Model.CommandSpec
import picocli.CommandLine.Spec

/**
 * Parent command for Cassandra-related operations.
 *
 * This command groups subcommands for Cassandra cluster management and tooling including:
 * - Cluster lifecycle: up, down, start, stop, restart
 * - Configuration: use, list, download-config, write-config, update-config
 * - Stress testing: stress (with nested subcommands)
 *
 * Note: Sub-commands are registered manually in CommandLineParser to inject
 * the Context dependency that PicoCommands require.
 */
@Command(
    name = "cassandra",
    description = ["Cassandra cluster management and tooling operations"],
    mixinStandardHelpOptions = true,
)
class Cassandra : Runnable {
    @Spec
    lateinit var spec: CommandSpec

    override fun run() {
        spec.commandLine().usage(System.out)
    }
}
