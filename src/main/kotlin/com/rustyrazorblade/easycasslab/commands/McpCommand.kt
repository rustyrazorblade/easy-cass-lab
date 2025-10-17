package com.rustyrazorblade.easycasslab.commands

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import com.rustyrazorblade.easycasslab.Constants
import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.annotations.RequireDocker
import com.rustyrazorblade.easycasslab.mcp.McpServer
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Command to start the MCP (Model Context Protocol) server. This server exposes all easy-cass-lab
 * commands as MCP tools, allowing AI assistants and other MCP clients to interact with the tool
 * programmatically.
 */
@RequireDocker
@Parameters(
    commandDescription =
        "Start MCP server for AI assistant integration. " +
            "Add to claude with: claude mcp add --transport sse easy-cass-lab http://127.0.0.1:8888/sse",
)
class McpCommand(context: Context) : BaseCommand(context) {
    @Parameter(description = "MCP server port", names = ["--port", "-p"])
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
        val config = McpConfiguration(
            mcpServers = mapOf(
                "easy-cass-mcp" to McpServerConfig(
                    type = "http",
                    url = "http://localhost:${Constants.Network.EASY_CASS_MCP_PORT}/mcp"
                ),
                "easy-cass-lab" to McpServerConfig(
                    type = "sse",
                    url = "http://localhost:$port/sse"
                ),
                "cassandra-easy-stress" to McpServerConfig(
                    type = "sse",
                    url = "http://localhost:${Constants.Network.CASSANDRA_EASY_STRESS_PORT}/sse"
                )
            )
        )

        val json = Json { prettyPrint = true }
        val configFile = File(".mcp.json")
        configFile.writeText(json.encodeToString(config))

        return configFile
    }

    override fun execute() {
        log.info { "Starting easy-cass-lab MCP server..." }

        // Generate the .mcp.json file
        val configFile = generateMcpConfig()
        outputHandler.handleMessage(
            """
            MCP configuration saved to: ${configFile.absolutePath}

            """.trimIndent()
        )

        try {
            // Create and start the MCP server with SDK
            val server = McpServer(context)
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
