package com.rustyrazorblade.easydblab.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import com.rustyrazorblade.easydblab.Constants
import com.rustyrazorblade.easydblab.Context
import com.rustyrazorblade.easydblab.output.OutputHandler
import io.github.oshai.kotlinlogging.KotlinLogging
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper
import io.modelcontextprotocol.server.McpServer
import io.modelcontextprotocol.server.McpServerFeatures.SyncPromptSpecification
import io.modelcontextprotocol.server.McpSyncServer
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider
import io.modelcontextprotocol.spec.McpSchema
import org.eclipse.jetty.ee10.servlet.ServletContextHandler
import org.eclipse.jetty.ee10.servlet.ServletHolder
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.ServerConnector
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * MCP server implementation using the Java MCP SDK with embedded Jetty.
 *
 * Key features:
 * - Synchronous tool execution (no background polling needed)
 * - Commands block until completion and return results directly
 * - Uses Jetty 12 with Jakarta EE 10 Servlet API
 * - Streamable HTTP transport for MCP communication
 */
class McpServerImpl(
    private val context: Context,
) : KoinComponent {
    companion object {
        private val log = KotlinLogging.logger {}
        private const val MCP_ENDPOINT = "/mcp"
    }

    private val outputHandler: OutputHandler by inject()
    private val toolRegistry = McpToolRegistry(context)

    private lateinit var jettyServer: Server
    private lateinit var mcpServer: McpSyncServer

    /**
     * Start the MCP server on the specified port.
     * This method blocks until the server is stopped.
     */
    fun start(port: Int) {
        log.info { "Starting MCP server with Java SDK (version ${context.version})" }

        // Create Streamable HTTP transport provider using builder
        val transportProvider =
            HttpServletStreamableServerTransportProvider
                .builder()
                .jsonMapper(JacksonMcpJsonMapper(ObjectMapper()))
                .mcpEndpoint(MCP_ENDPOINT)
                .build()

        // Create sync MCP server with capabilities using builder
        val capabilities =
            McpSchema.ServerCapabilities
                .builder()
                .prompts(false) // listChanged = false
                .tools(false) // listChanged = false
                .build()

        mcpServer =
            McpServer
                .sync(transportProvider)
                .serverInfo("easy-db-lab", context.version.toString())
                .capabilities(capabilities)
                .build()

        // Register tools and prompts
        registerTools(mcpServer)
        registerPrompts(mcpServer)

        outputHandler.publishMessage(
            """
            Starting Streamable MCP server on port $port:

            http://127.0.0.1:$port${MCP_ENDPOINT}
            """.trimIndent(),
        )

        jettyServer = checkNotNull(createJettyServer(port, transportProvider))
        with(jettyServer) {
            start()
            join()
        }
    }

    private fun createJettyServer(
        port: Int,
        transportProvider: HttpServletStreamableServerTransportProvider,
    ): Server =
        Server().apply {
            // Bind to localhost only for security
            addConnector(
                ServerConnector(this).apply {
                    host = "127.0.0.1"
                    this.port = port
                    // Set idle timeout to match tool execution timeout (plus buffer)
                    // Default Jetty timeout is 30 seconds which is too short for long-running tools
                    idleTimeout = Constants.MCP.IDLE_TIMEOUT_MS
                },
            )
            handler =
                ServletContextHandler().apply {
                    contextPath = "/"
                    addServlet(ServletHolder(transportProvider), "/*")
                }
        }

    private fun registerTools(server: McpSyncServer) {
        val tools = toolRegistry.getToolSpecifications()
        log.info { "Registering ${tools.size} MCP-enabled tools" }

        tools.forEach { spec ->
            log.info { "Registering tool: ${spec.tool().name()}" }
            server.addTool(spec)
        }
    }

    private fun registerPrompts(server: McpSyncServer) {
        val loader = PromptLoader()
        val prompts = loader.loadAllPrompts("com.rustyrazorblade.mcp")

        log.info { "Registering ${prompts.size} prompts" }

        prompts.forEach { promptResource ->
            val spec = createPromptSpecification(promptResource)
            log.info { "Registering prompt: ${promptResource.name}" }
            server.addPrompt(spec)
        }
    }

    private fun createPromptSpecification(resource: PromptResource): SyncPromptSpecification {
        val prompt =
            McpSchema.Prompt(
                resource.name,
                resource.description,
                emptyList(), // no arguments
            )

        return SyncPromptSpecification(prompt) { _, _ ->
            val message =
                McpSchema.PromptMessage(
                    McpSchema.Role.USER,
                    McpSchema.TextContent(resource.content),
                )
            McpSchema.GetPromptResult(
                resource.description,
                listOf(message),
            )
        }
    }

    fun stop() {
        jettyServer.takeIf { it.isRunning }?.stop()
        mcpServer.close()
    }
}
