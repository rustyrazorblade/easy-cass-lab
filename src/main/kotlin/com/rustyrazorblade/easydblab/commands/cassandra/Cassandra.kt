package com.rustyrazorblade.easydblab.commands.cassandra

import picocli.CommandLine.Command

/**
 * Parent command for Cassandra-related operations.
 *
 * This command groups subcommands for Cassandra tooling including
 * stress testing with cassandra-easy-stress.
 */
@Command(
    name = "cassandra",
    description = ["Cassandra tooling operations"],
    subcommands = [],
)
class Cassandra : Runnable {
    override fun run() {
        println("Use a subcommand. Run 'easy-db-lab cassandra --help' for available commands.")
    }
}
