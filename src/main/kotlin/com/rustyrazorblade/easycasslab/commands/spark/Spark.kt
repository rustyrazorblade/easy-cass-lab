package com.rustyrazorblade.easycasslab.commands.spark

import picocli.CommandLine.Command
import picocli.CommandLine.Model.CommandSpec
import picocli.CommandLine.Spec

/**
 * Parent command for Spark EMR cluster operations.
 *
 * This command serves as a container for Spark-related sub-commands including
 * job submission, status checking, and job listing. When invoked without a
 * sub-command, it displays usage help.
 *
 * Available sub-commands:
 * - submit: Submit a Spark job to the EMR cluster
 * - status: Check the status of a Spark job (defaults to most recent)
 * - jobs: List recent Spark jobs on the cluster
 *
 * Note: Sub-commands are registered manually in CommandLineParser to inject
 * the Context dependency that PicoCommands require.
 */
@Command(
    name = "spark",
    description = ["Spark EMR cluster operations"],
    mixinStandardHelpOptions = true,
)
class Spark : Runnable {
    @Spec
    lateinit var spec: CommandSpec

    override fun run() {
        spec.commandLine().usage(System.out)
    }
}
