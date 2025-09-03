package com.rustyrazorblade.easycasslab.mcp

import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.output.OutputHandler
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.modelcontextprotocol.kotlin.sdk.*
import io.modelcontextprotocol.kotlin.sdk.server.*
import kotlinx.coroutines.runBlocking
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.Semaphore
import kotlin.getValue

/**
 * MCP server implementation using the official SDK.
 */
class McpServer(private val context: Context) : KoinComponent {
    companion object {
        private val log = KotlinLogging.logger {}
    }

    protected val outputHandler: OutputHandler by inject()

    private val toolRegistry = McpToolRegistry(context)
    private val executionSemaphore = Semaphore(1) // Only allow one tool execution at a time

    fun start(port: Int) {
        try {
            log.info { "Starting MCP server with SDK (version ${context.version})" }

            // Create server instance
            val server =
                Server(
                    serverInfo =
                        Implementation(
                            name = "easy-cass-lab",
                            version = context.version.toString(),
                        ),
                    options =
                        ServerOptions(
                            capabilities =
                                ServerCapabilities(
                                    tools =
                                        ServerCapabilities.Tools(
                                            listChanged = true,
                                        ),
                                    logging = null, // Explicitly disable logging capability
                                ),
                        ),
                )

            // Get tools from registry (already filtered by @McpCommand annotation)
            val tools = toolRegistry.getTools()

            log.info { "Registering ${tools.size} MCP-enabled tools" }
            log.info { "Tools to register: ${tools.map { it.name }}" }

            tools.forEach { toolInfo ->
                log.info { "Registering tool: ${toolInfo.name} with description: ${toolInfo.description}" }
                server.addTool(
                    name = toolInfo.name,
                    description = toolInfo.description,
                    inputSchema = Tool.Input(toolInfo.inputSchema),
                    handler = { request ->
                        // Try to acquire the semaphore
                        if (!executionSemaphore.tryAcquire()) {
                            log.warn { "Tool execution already in progress, rejecting request for ${request.name}" }
                            CallToolResult(
                                content =
                                    listOf(
                                        TextContent(text = "Another tool is currently executing. Please wait and try again."),
                                    ),
                                isError = true,
                            )
                        } else {
                            try {
                                val result = toolRegistry.executeTool(request.name, request.arguments)
                                CallToolResult(
                                    content =
                                        result.content.map { text ->
                                            TextContent(text = text)
                                        },
                                    isError = result.isError,
                                )
                            } finally {
                                // Always release the semaphore
                                executionSemaphore.release()
                            }
                        }
                    },
                )
            }

            outputHandler.handleMessage(
                """
                Starting MCP server.  You can add it to claude code by doing the following:
                
                claude mcp add --transport sse easy-cass-lab http://127.0.0.1:$port/sse
                """.trimIndent(),
            )
            // Create a KTor application here
            // register the SSE plugin
            runBlocking {
                embeddedServer(CIO, host = "0.0.0.0", port = port) {
                    mcp {
                        server
                    }
                }.startSuspend(wait = true)
            }

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
