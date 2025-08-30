package com.rustyrazorblade.easycasslab.mcp

import com.rustyrazorblade.easycasslab.Context
import io.github.oshai.kotlinlogging.KotlinLogging
import io.modelcontextprotocol.kotlin.sdk.*
import io.modelcontextprotocol.kotlin.sdk.server.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.io.*

/**
 * MCP server implementation using the official SDK.
 */
class McpServer(private val context: Context) {
    companion object {
        private val log = KotlinLogging.logger {}
    }
    
    private val toolRegistry = McpToolRegistry(context)
    
    fun start() {
        try {
            log.info { "Starting MCP server with SDK (version ${context.version})" }
            
            // Create server instance
            val server = Server(
                serverInfo = Implementation(
                    name = "easy-cass-lab",
                    version = context.version.toString()
                ),
                options = ServerOptions(
                    capabilities = ServerCapabilities(
                        tools = ServerCapabilities.Tools(
                            listChanged = true
                        )
                    )
                )
            )
            
            // Add all tools from registry
            toolRegistry.getTools().forEach { toolInfo ->
                server.addTool(
                    name = toolInfo.name,
                    description = toolInfo.description,
                    inputSchema = Tool.Input(toolInfo.inputSchema),
                    handler = { request ->
                        val result = toolRegistry.executeTool(request.name, request.arguments)
                        CallToolResult(
                            content = result.content.map { text ->
                                TextContent(text = text)
                            },
                            isError = result.isError
                        )
                    }
                )
            }
            
            // Set up stdio transport using kotlinx-io
            val transport = StdioServerTransport(
                inputStream = System.`in`.asSource().buffered(),
                outputStream = System.out.asSink().buffered()
            )
            
            // Connect server to transport
            runBlocking {
                server.connect(transport)
                log.info { "MCP server running, waiting for messages..." }
                val done = Job()
                server.onClose {
                    done.complete()
                }
                done.join()
            }

            // The server runs in the transport's coroutine scope
            // We need to keep the main coroutine alive
            // The transport will handle the message loop

            // Block here to keep the server running
            // The server will run until the transport is closed

            log.info { "MCP server stopped" }
        } catch (e: IllegalStateException) {
            log.error { "Transport error: ${e.message}" }
            throw e
        } catch (e: Exception) {
            log.error(e) { "Unexpected error in MCP server" }
            throw e
        }
    }
}