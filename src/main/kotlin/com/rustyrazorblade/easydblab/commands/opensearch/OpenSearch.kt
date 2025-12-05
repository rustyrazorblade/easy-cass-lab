package com.rustyrazorblade.easydblab.commands.opensearch

import picocli.CommandLine.Command
import picocli.CommandLine.Model.CommandSpec
import picocli.CommandLine.Spec

/**
 * Parent command for AWS OpenSearch operations.
 *
 * This command serves as a container for OpenSearch-related sub-commands including
 * creating, deleting, and checking status of AWS-managed OpenSearch domains.
 *
 * Available sub-commands:
 * - start: Create an OpenSearch domain
 * - status: Check OpenSearch domain status
 * - stop: Delete the OpenSearch domain
 */
@Command(
    name = "opensearch",
    description = ["AWS OpenSearch domain operations"],
    mixinStandardHelpOptions = true,
)
class OpenSearch : Runnable {
    @Spec
    lateinit var spec: CommandSpec

    override fun run() {
        spec.commandLine().usage(System.out)
    }
}
