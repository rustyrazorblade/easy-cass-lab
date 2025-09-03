package com.rustyrazorblade.easycasslab.mcp

import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.output.CompositeOutputHandler
import com.rustyrazorblade.easycasslab.output.FilteringChannelOutputHandler
import com.rustyrazorblade.easycasslab.output.OutputEvent
import com.rustyrazorblade.easycasslab.output.OutputHandler
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.netty.Netty
import io.ktor.server.engine.embeddedServer
import io.modelcontextprotocol.kotlin.sdk.*
import io.modelcontextprotocol.kotlin.sdk.server.*
import kotlinx.serialization.json.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
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
    
    // Status tracking components
    private val outputChannel = Channel<OutputEvent>(Channel.UNLIMITED)
    private lateinit var streamingHandler: FilteringChannelOutputHandler
    private var streamingInitialized = false
    private val messageBuffer = java.util.Collections.synchronizedList(mutableListOf<String>())
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
                handler = { _ ->
                    val isRunning = executionSemaphore.availablePermits() == 0
                    val status = when {
                        isRunning -> "running"
                        currentCommand != null -> "completed"
                        else -> "idle"
                    }
                    
                    val runtimeSeconds = if (commandStartTime != null) {
                        (System.currentTimeMillis() - commandStartTime!!) / 1000
                    } else 0
                    
                    // Get accumulated messages and clear buffer
                    val messages = messageBuffer.toList()
                    messageBuffer.clear()
                    
                    val statusResponse = """
                        {
                            "status": "$status",
                            "command": "${currentCommand ?: "none"}",
                            "runtime_seconds": $runtimeSeconds,
                            "messages": [${messages.joinToString(",") { "\"${it.replace("\"", "\\\"")}\"" }}],
                            "timestamp": "${java.time.Instant.now()}"
                        }
                    """.trimIndent()
                    
                    // Clear command tracking if completed
                    if (status == "completed") {
                        currentCommand = null
                        commandStartTime = null
                    }
                    
                    CallToolResult(
                        content = listOf(TextContent(text = statusResponse)),
                        isError = false
                    )
                }
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
                        // Return immediate response indicating background execution
                        val response = CallToolResult(
                            content = listOf(
                                TextContent(text = "Tool '${request.name}' started in background. Use 'get_status' to monitor progress.")
                            ),
                            isError = false
                        )
                        
                        // Launch background execution using simple Thread
                        Thread {
                            try {
                                // Try to acquire the semaphore in background
                                executionSemaphore.acquire() // Block until available
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
                                    outputHandler.handleMessage("Background execution of tool '${request.name}' complete.")
                                } finally {
                                    executionSemaphore.release()
                                }
                            } catch (e: Exception) {
                                log.error(e) { "Error in background execution of tool ${request.name}" }
                                outputHandler.handleError("Background execution of tool '${request.name}' failed: ${e.message}", e)
                            }
                        }.start()
                        
                        // Return immediate response
                        response
                    },
                )
            }

            // Add provision prompt
            log.info { "Registering provision prompt" }
            server.addPrompt(
                name = "provision",
                description = "Step-by-step guide for provisioning a new cluster"
            ) { request ->
                GetPromptResult(
                    description = "Complete guide for provisioning a new Cassandra cluster",
                    messages = listOf(
                        PromptMessage(
                            role = Role.entries.first { it.toString().lowercase() == "user" },
                            content = TextContent(
                                text = """
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
                                - Call 'get_status' until status shows 'idle' before proceeding
                                - Long-running commands (especially 'up' and 'start') may take several minutes
                                
                                You can now run load tests and perform cluster analysis.
                                
                                When done, call down with autoApprove: true to shut the cluster down.
                                """.trimIndent()
                            )
                        )
                    )
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
                embeddedServer(Netty, host = "0.0.0.0", port = port) {
                    mcp {
                        server
                    }
                    
                    // Start coroutine to bridge output events to message buffer
                    launch {
                        log.info { "Message buffer bridge coroutine started successfully" }
                        for (event in outputChannel) {
                            try {
                                when (event) {
                                    is OutputEvent.MessageEvent -> {
                                        val timestampedMessage = "[${java.time.LocalTime.now()}] ${event.message}"
                                        messageBuffer.add(timestampedMessage)
                                        log.info { "Buffered message: ${event.message}" }
                                    }
                                    is OutputEvent.ErrorEvent -> {
                                        val timestampedMessage = "[${java.time.LocalTime.now()}] ERROR: ${event.message}"
                                        messageBuffer.add(timestampedMessage)
                                        log.error { "Buffered error: ${event.message}" }
                                    }
                                    is OutputEvent.CloseEvent -> {
                                        log.debug { "Output channel closed" }
                                        break
                                    }
                                    is OutputEvent.FrameEvent -> {
                                        // Frame events are filtered by FilteringChannelOutputHandler
                                        // but we'll handle them if they come through
                                        log.debug { "Frame event received" }
                                    }
                                }
                            } catch (e: Exception) {
                                log.error(e) { "Error processing output event for message buffer" }
                            }
                        }
                        log.debug { "Output channel processing completed" }
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
