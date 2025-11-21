package com.rustyrazorblade.easycasslab.commands

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import com.beust.jcommander.ParametersDelegate
import com.github.ajalt.mordant.TermColors
import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.annotations.RequireProfileSetup
import com.rustyrazorblade.easycasslab.annotations.RequireSSHKey
import com.rustyrazorblade.easycasslab.commands.converters.ServerTypeConverter
import com.rustyrazorblade.easycasslab.commands.delegates.Hosts
import com.rustyrazorblade.easycasslab.configuration.Host
import com.rustyrazorblade.easycasslab.configuration.ServerType

/**
 * Command to execute shell commands on remote hosts over SSH.
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
@Parameters(commandDescription = "Execute a shell command on remote hosts")
class Exec(
    context: Context,
) : BaseCommand(context) {
    @ParametersDelegate
    var hosts = Hosts()

    @Parameter(
        description = "Server type (cassandra, stress, control)",
        names = ["--type", "-t"],
        converter = ServerTypeConverter::class,
    )
    var serverType: ServerType = ServerType.Cassandra

    @Parameter(
        description = "Command to execute",
        required = true,
    )
    var command: MutableList<String> = mutableListOf()

    @Parameter(
        description = "Execute in parallel",
        names = ["-p"],
    )
    var parallel: Boolean = false

    override fun execute() {
        val commandString = command.joinToString(" ")

        if (commandString.isBlank()) {
            outputHandler.handleError("Command cannot be empty", null)
            return
        }

        val hosts = tfstate.getHosts(serverType)
        if (hosts.isEmpty()) {
            outputHandler.handleMessage("No hosts found for server type: $serverType")
            return
        }

        // Execute on hosts using tfstate.withHosts for consistent behavior
        tfstate.withHosts(serverType, this.hosts, parallel = parallel) { host ->
            executeOnHost(host, commandString)
        }
    }

    /**
     * Execute command on a single host and display output with color-coded header.
     *
     * @param host The host to execute on
     * @param commandString The command to execute
     */
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
