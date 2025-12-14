package com.rustyrazorblade.easydblab.commands

import com.rustyrazorblade.easydblab.Constants
import com.rustyrazorblade.easydblab.Context
import com.rustyrazorblade.easydblab.annotations.RequireDocker
import com.rustyrazorblade.easydblab.annotations.RequireProfileSetup
import com.rustyrazorblade.easydblab.mcp.McpServerImpl
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import java.io.File

/**
 * Starts the MCP (Model Context Protocol) server for AI assistant integration.
 */
@RequireDocker
@RequireProfileSetup
@Command(
    name = "server",
    description = [
        "Start server for AI assistant integration with MCP. " +
            "Add to claude with: claude mcp add easy-db-lab http://127.0.0.1:8888/mcp",
    ],
)
class Server(
    context: Context,
) : PicoBaseCommand(context) {
    @Option(
        names = ["--port", "-p"],
        description = ["MCP server port"],
    )
    var port: Int = Constants.Network.DEFAULT_MCP_PORT

    companion object {
        private val log = KotlinLogging.logger {}
    }

    @Serializable
    data class McpServerConfig(
        val type: String,
        val url: String,
    )

    @Serializable
    data class McpConfiguration(
        val mcpServers: Map<String, McpServerConfig>,
    )

    private fun generateMcpConfig(): File {
        val config =
            McpConfiguration(
                mcpServers =
                    mapOf(
                        "easy-db-lab" to
                            McpServerConfig(
                                type = "http",
                                url = "http://localhost:$port/mcp",
                            ),
                    ),
            )

        val json = Json { prettyPrint = true }
        val configFile = File(".mcp.json")
        configFile.writeText(json.encodeToString(config))

        return configFile
    }

    override fun execute() {
        log.info { "Starting easy-db-lab server with Streamable HTTP MCP..." }

        // Generate the .mcp.json file
        val configFile = generateMcpConfig()
        outputHandler.handleMessage(
            """
            MCP configuration saved to: ${configFile.absolutePath}

            """.trimIndent(),
        )

        try {
            // Create and start the MCP server with Java SDK
            val server = McpServerImpl(context)
            server.start(port)

            // The server will run until interrupted
            log.info { "MCP server stopped." }
        } catch (e: RuntimeException) {
            log.error(e) { "Failed to start MCP server" }
            System.err.println("Failed to start MCP server: ${e.message}")
            throw e
        }
    }
}
