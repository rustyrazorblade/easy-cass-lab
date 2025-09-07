package com.rustyrazorblade.easycasslab.mcp

import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.output.CompositeOutputHandler
import com.rustyrazorblade.easycasslab.output.FilteringChannelOutputHandler
import com.rustyrazorblade.easycasslab.output.OutputEvent
import com.rustyrazorblade.easycasslab.output.OutputHandler
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.modelcontextprotocol.kotlin.sdk.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.GetPromptResult
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.PromptMessage
import io.modelcontextprotocol.kotlin.sdk.Role
import io.modelcontextprotocol.kotlin.sdk.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.Tool
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.mcp
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.Semaphore
import kotlin.concurrent.thread
import kotlin.getValue

@Serializable
data class StatusResponse(
    val status: String,
    val command: String,
    val runtime_seconds: Long,
    val messages: List<String>,
    val timestamp: String,
)

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

    // Status tracking components
    private val outputChannel = Channel<OutputEvent>(Channel.UNLIMITED)
    private lateinit var streamingHandler: FilteringChannelOutputHandler
    private var streamingInitialized = false
    private val messageBuffer = ChannelMessageBuffer(outputChannel)
    private var currentCommand: String? = null
    private var commandStartTime: Long? = null

    /**
     * Initialize streaming functionality by adding FilteringChannelOutputHandler to the CompositeOutputHandler.
     * This method is idempotent - it will not add duplicate handlers if called multiple times.
     */
    fun initializeStreaming() {
        if (streamingInitialized) {
            log.debug { "Streaming already initialized, skipping" }
            return
        }

        streamingHandler = FilteringChannelOutputHandler(outputChannel)

        val compositeHandler = outputHandler as CompositeOutputHandler
        compositeHandler.addHandler(streamingHandler)
        streamingInitialized = true
        log.info { "Streaming handler added to composite output handler" }
    }

    /**
     * Creates a handler for the get_status tool that returns current execution status and messages.
     */
    private fun createStatusHandler(): (CallToolRequest) -> CallToolResult =
        { _ ->
            val isRunning = executionSemaphore.availablePermits() == 0
            val status =
                when {
                    isRunning -> "running"
                    currentCommand != null -> "completed"
                    else -> "idle"
                }

            val runtimeSeconds =
                if (commandStartTime != null) {
                    (System.currentTimeMillis() - commandStartTime!!) / 1000
                } else {
                    0
                }

            // Get accumulated messages and clear buffer
            val messages = messageBuffer.getAndClearMessages()

            val statusResponse =
                StatusResponse(
                    status = status,
                    command = currentCommand ?: "none",
                    runtime_seconds = runtimeSeconds,
                    messages = messages,
                    timestamp = java.time.Instant.now().toString(),
                )

            // Clear command tracking if completed
            if (status == "completed") {
                currentCommand = null
                commandStartTime = null
            }

            CallToolResult(
                content = listOf(TextContent(text = Json.encodeToString(statusResponse))),
                isError = false,
            )
        }

    /**
     * Creates a tool handler for background execution with proper error handling and status tracking.
     */
    private fun createToolHandler(): (CallToolRequest) -> CallToolResult =
        { request ->
            // Try to acquire semaphore without blocking
            if (!executionSemaphore.tryAcquire()) {
                // Another command is already running
                CallToolResult(
                    content =
                        listOf(
                            TextContent(
                                text = "Another tool is already running. " +
                                    "Please wait for it to complete or check status with 'get_status'.",
                            ),
                        ),
                    isError = false,
                )
            } else {
                // Launch background execution using simple Thread
                thread(start = true) {
                    try {
                        log.info { "Starting background execution of tool: ${request.name}" }

                        // Track command execution
                        currentCommand = request.name
                        commandStartTime = System.currentTimeMillis()

                        // Reset frame count for this tool execution
                        if (::streamingHandler.isInitialized) {
                            streamingHandler.resetFrameCount()
                        }

                        toolRegistry.executeTool(request.name, request.arguments)
                        log.info { "Completed background execution of tool: ${request.name}" }
                        outputHandler.handleMessage(
                            "Background execution of tool '${request.name}' complete.",
                        )
                    } catch (e: Exception) {
                        log.error(e) { "Error in background execution of tool ${request.name}" }
                        outputHandler.handleError(
                            "Background execution of tool '${request.name}' failed: ${e.message}",
                            e,
                        )
                    } finally {
                        executionSemaphore.release()
                    }
                }

                // Return immediate response indicating background execution
                CallToolResult(
                    content =
                        listOf(
                            TextContent(text = "Tool '${request.name}' started in background. Use 'get_status' to monitor progress."),
                        ),
                    isError = false,
                )
            }
        }

    private fun registerTools(server: Server) {
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
                handler = createToolHandler(),
            )
        }
    }

    /**
     * Creates and adds the provision prompt to the server.
     */
    private fun createProvisionPrompt(server: Server) {
        log.info { "Registering provision prompt" }
        server.addPrompt(
            name = "provision",
            description = "Step-by-step guide for provisioning a new cluster",
        ) { request ->
            GetPromptResult(
                description = "Complete guide for provisioning a new Cassandra cluster",
                messages =
                    listOf(
                        PromptMessage(
                            role = Role.entries.first { it.toString().lowercase() == "user" },
                            content =
                                TextContent(
                                    text =
                                        """
                                        Cluster Provisioning Guide, follow these steps:
                                        
                                        1. Initialize cluster: call 'init' with start: false
                                        2. Provision infrastructure: call 'up' 
                                        3. Set Cassandra version: call 'use' with your desired version
                                        4. Check for configuration updates: review if any config changes are needed
                                        5. Update configuration: if config changes are required, update cassandra.patch.yaml and then call 'update-config'
                                        6. Start services: call 'start'
                                        
                                        IMPORTANT: Commands run asynchronously in the background to avoid timeouts.
                                        
                                        - Each command returns immediately with a "started in background" message
                                        - Use 'get_status' to monitor progress and see accumulated log messages
                                        - Call 'get_status' once a second until status shows 'idle' before proceeding
                                        - Long-running commands (especially 'up' and 'start') may take several minutes
                                        
                                        You can now run load tests and perform cluster analysis.
                                        
                                        When done, call down with autoApprove: true to shut the cluster down.
                                        """.trimIndent(),
                                ),
                        ),
                    ),
            )
        }
    }

    fun start(port: Int) {
        try {
            log.info { "Starting MCP server with SDK (version ${context.version})" }

            // Initialize streaming functionality
            initializeStreaming()

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
                                    prompts =
                                        ServerCapabilities.Prompts(
                                            listChanged = true,
                                        ),
                                    logging = null, // Explicitly disable logging capability
                                ),
                        ),
                )

            // Add get_status tool first
            log.info { "Registering get_status tool" }
            server.addTool(
                name = "get_status",
                description = "Get the status of background tool execution and accumulated messages",
                inputSchema = Tool.Input(buildJsonObject { /* no parameters needed */ }),
                handler = createStatusHandler(),
            )

            // Register all MCP tools
            registerTools(server)

            // Add provision prompt
            createProvisionPrompt(server)

            outputHandler.handleMessage(
                """
                Starting MCP server.  You can add it to claude code by doing the following:
                
                claude mcp add --transport sse easy-cass-lab http://127.0.0.1:$port/sse
                """.trimIndent(),
            )
            // Start the message buffer consumer
            messageBuffer.start()

            // Create a KTor application here
            // register the SSE plugin
            embeddedServer(Netty, host = "0.0.0.0", port = port) {
                mcp {
                    server
                }
            }.start(wait = true)

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
