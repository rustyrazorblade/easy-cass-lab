package com.rustyrazorblade.easydblab.commands.k8

import picocli.CommandLine.Command
import picocli.CommandLine.Model.CommandSpec
import picocli.CommandLine.Spec

/**
 * Parent command for Kubernetes cluster operations.
 *
 * This command serves as a container for K8s-related sub-commands including
 * applying observability manifests. When invoked without a sub-command,
 * it displays usage help.
 *
 * Available sub-commands:
 * - apply: Apply observability stack (OTel, Prometheus, Grafana) to K8s cluster
 */
@Command(
    name = "k8",
    description = ["Kubernetes cluster operations"],
    mixinStandardHelpOptions = true,
    subcommands = [
        K8Apply::class,
    ],
)
class K8 : Runnable {
    @Spec
    lateinit var spec: CommandSpec

    override fun run() {
        spec.commandLine().usage(System.out)
    }
}
