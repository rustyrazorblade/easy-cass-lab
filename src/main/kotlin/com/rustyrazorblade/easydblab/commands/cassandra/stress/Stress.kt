package com.rustyrazorblade.easydblab.commands.cassandra.stress

import picocli.CommandLine.Command
import picocli.CommandLine.Model.CommandSpec
import picocli.CommandLine.Spec

/**
 * Parent command for cassandra-easy-stress operations on K8s.
 *
 * This command serves as a container for stress testing sub-commands that run
 * cassandra-easy-stress as Kubernetes Jobs on the K3s cluster.
 *
 * Available sub-commands:
 * - start: Start a stress job on K8s
 * - status: Check status of stress jobs
 * - stop: Delete stress jobs
 * - logs: View logs from stress jobs
 */
@Command(
    name = "stress",
    description = ["Cassandra stress testing operations on K8s"],
    mixinStandardHelpOptions = true,
)
class Stress : Runnable {
    @Spec
    lateinit var spec: CommandSpec

    override fun run() {
        spec.commandLine().usage(System.out)
    }
}
