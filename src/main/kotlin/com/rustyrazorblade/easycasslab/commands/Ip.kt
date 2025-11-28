package com.rustyrazorblade.easycasslab.commands

import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.annotations.McpCommand
import com.rustyrazorblade.easycasslab.annotations.RequireProfileSetup
import com.rustyrazorblade.easycasslab.configuration.ServerType
import com.rustyrazorblade.easycasslab.di.TFStateProvider
import com.rustyrazorblade.easycasslab.output.OutputHandler
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters

/**
 * Get IP address for a host by alias.
 *
 * This command looks up a host by its alias (e.g., cassandra0, stress0) and
 * returns either its public or private IP address. Useful for scripts that
 * need to pass IP addresses to other commands.
 *
 * Examples:
 *   easy-cass-lab ip cassandra0           # Returns public IP (default)
 *   easy-cass-lab ip cassandra0 --public  # Returns public IP
 *   easy-cass-lab ip cassandra0 --private # Returns private IP
 */
@McpCommand
@RequireProfileSetup
@Command(
    name = "ip",
    description = ["Get IP address for a host by alias"],
)
class Ip(
    @Suppress("UnusedPrivateProperty") private val context: Context,
) : PicoCommand,
    KoinComponent {
    private val outputHandler: OutputHandler by inject()
    private val tfStateProvider: TFStateProvider by inject()
    private val tfstate by lazy { tfStateProvider.getDefault() }

    @Parameters(
        index = "0",
        description = ["Host alias (e.g., cassandra0, stress0)"],
    )
    lateinit var host: String

    @Option(names = ["--public"], description = ["Return public IP (default)"])
    var publicIp: Boolean = false

    @Option(names = ["--private"], description = ["Return private IP"])
    var privateIp: Boolean = false

    override fun execute() {
        // Find the host across all server types
        val foundHost =
            ServerType.entries
                .flatMap { tfstate.getHosts(it) }
                .firstOrNull { it.alias == host }
                ?: error("Host not found: $host")

        // Default to public if neither flag specified
        val ip =
            when {
                privateIp -> foundHost.private
                else -> foundHost.public
            }

        outputHandler.handleMessage(ip)
    }
}
