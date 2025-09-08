package com.rustyrazorblade.easycasslab.commands

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.annotations.RequireDocker
import com.rustyrazorblade.easycasslab.mcp.McpServer
import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * Command to start the MCP (Model Context Protocol) server.
 * This server exposes all easy-cass-lab commands as MCP tools,
 * allowing AI assistants and other MCP clients to interact with
 * the tool programmatically.
 */
@RequireDocker
@Parameters(
    commandDescription =
        "Start MCP server for AI assistant integration. " +
            "Add to claude with: claude mcp add --transport sse easy-cass-lab http://127.0.0.1:8888/sse",
)
class McpCommand(context: Context) : BaseCommand(context) {
    @Parameter(description = "MCP server port", names = ["--port", "-p"])
    var port: Int = 8888

    companion object {
        private val log = KotlinLogging.logger {}
    }

    override fun execute() {
        log.info { "Starting easy-cass-lab MCP server..." }

        try {
            // Create and start the MCP server with SDK
            val server = McpServer(context)
            server.start(port)

            // The server will run until interrupted
            log.info { "MCP server stopped." }
        } catch (e: Exception) {
            log.error(e) { "Failed to start MCP server" }
            System.err.println("Failed to start MCP server: ${e.message}")
            throw e
        }
    }
}
