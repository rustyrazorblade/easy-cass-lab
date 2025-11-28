package com.rustyrazorblade.easycasslab.commands

import com.github.ajalt.mordant.TermColors
import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.annotations.RequireProfileSetup
import com.rustyrazorblade.easycasslab.annotations.RequireSSHKey
import com.rustyrazorblade.easycasslab.commands.converters.PicoServerTypeConverter
import com.rustyrazorblade.easycasslab.commands.mixins.HostsMixin
import com.rustyrazorblade.easycasslab.configuration.Host
import com.rustyrazorblade.easycasslab.configuration.ServerType
import picocli.CommandLine.Command
import picocli.CommandLine.Mixin
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters

/**
 * Execute shell commands on remote hosts over SSH.
 *
 * This command allows executing arbitrary shell commands on remote hosts with support for:
 * - Server type filtering (cassandra, stress, control)
 * - Host filtering via comma-separated host list
 * - Sequential or parallel execution
 * - Color-coded output (green for success, red for errors)
 * - Non-interleaved output display
 */
@RequireProfileSetup
@RequireSSHKey
@Command(
    name = "exec",
    description = ["Execute a shell command on remote hosts"],
)
class Exec(
    context: Context,
) : PicoBaseCommand(context) {
    @Mixin
    var hosts = HostsMixin()

    @Option(
        names = ["--type", "-t"],
        description = ["Server type (cassandra, stress, control)"],
        converter = [PicoServerTypeConverter::class],
    )
    var serverType: ServerType = ServerType.Cassandra

    @Parameters(
        description = ["Command to execute"],
        arity = "1..*",
    )
    lateinit var command: List<String>

    @Option(
        names = ["-p"],
        description = ["Execute in parallel"],
    )
    var parallel: Boolean = false

    override fun execute() {
        val commandString = command.joinToString(" ")

        if (commandString.isBlank()) {
            outputHandler.handleError("Command cannot be empty", null)
            return
        }

        val hostList = tfstate.getHosts(serverType)
        if (hostList.isEmpty()) {
            outputHandler.handleMessage("No hosts found for server type: $serverType")
            return
        }

        // Execute on hosts using tfstate.withHosts for consistent behavior
        tfstate.withHosts(serverType, hosts, parallel = parallel) { host ->
            executeOnHost(host, commandString)
        }
    }

    /**
     * Execute command on a single host and display output with color-coded header.
     *
     * @param host The host to execute on
     * @param commandString The command to execute
     */
    @Suppress("TooGenericExceptionCaught")
    private fun executeOnHost(
        host: Host,
        commandString: String,
    ) {
        try {
            val response = remoteOps.executeRemotely(host, commandString, output = true, secret = false)

            // Determine if there was an error (stderr present)
            val hasError = response.stderr.isNotEmpty()

            // Display output with color-coded header
            with(TermColors()) {
                val headerColor = if (hasError) red("=== ${host.alias} ===") else green("=== ${host.alias} ===")
                outputHandler.handleMessage(bold(headerColor))

                // Display stdout if present
                if (response.text.isNotEmpty()) {
                    outputHandler.handleMessage(response.text)
                }

                // Display stderr if present
                if (hasError) {
                    outputHandler.handleMessage(red(response.stderr))
                }
            }
        } catch (e: Exception) {
            // Handle execution failures
            with(TermColors()) {
                outputHandler.handleMessage(bold(red("=== ${host.alias} ===")))
                outputHandler.handleMessage(red("Error executing command: ${e.message}"))
            }
        }
    }
}
