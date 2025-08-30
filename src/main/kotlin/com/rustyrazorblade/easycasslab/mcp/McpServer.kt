package com.rustyrazorblade.easycasslab.mcp

import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.output.OutputHandler
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.engine.embeddedServer
import io.modelcontextprotocol.kotlin.sdk.*
import io.modelcontextprotocol.kotlin.sdk.server.*
import io.ktor.server.cio.CIO
import kotlinx.coroutines.runBlocking
import org.koin.core.component.inject
import kotlin.getValue
import org.koin.core.component.KoinComponent

/**
 * MCP server implementation using the official SDK.
 */
class McpServer(private val context: Context) : KoinComponent {
    companion object {
        private val log = KotlinLogging.logger {}
    }
    protected val outputHandler: OutputHandler by inject()
    
    private val toolRegistry = McpToolRegistry(context)
    
    fun start(port: Int) {
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
            outputHandler.handleMessage("""
                Starting MCP server.  You can add it to claude code by doing the following:
                
                claude mcp add --transport sse easy-cass-lab http://127.0.0.1:$port/sse
                    """.trimIndent())
            // Create a KTor application here
            // register the SSE plugin
            runBlocking {
                embeddedServer(CIO, host = "0.0.0.0", port = port) {
                    mcp {
                        server
                    }
//                    install(SSE)
//                    routing {
//                        sse("/sse") {
//                            val transport = SseServerTransport("/message", this)
//                            server.connect(transport)
//                        }
//                    }
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