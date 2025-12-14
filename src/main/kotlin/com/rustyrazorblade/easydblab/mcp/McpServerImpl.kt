package com.rustyrazorblade.easydblab.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import com.rustyrazorblade.easydblab.Context
import com.rustyrazorblade.easydblab.output.OutputHandler
import io.github.oshai.kotlinlogging.KotlinLogging
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper
import io.modelcontextprotocol.server.McpServer
import io.modelcontextprotocol.server.McpServerFeatures.SyncPromptSpecification
import io.modelcontextprotocol.server.McpSyncServer
import io.modelcontextprotocol.server.transport.HttpServletSseServerTransportProvider
import io.modelcontextprotocol.spec.McpSchema
import org.eclipse.jetty.ee10.servlet.ServletContextHandler
import org.eclipse.jetty.ee10.servlet.ServletHolder
import org.eclipse.jetty.server.Server
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * MCP server implementation using the Java MCP SDK with embedded Jetty.
 *
 * Key features:
 * - Synchronous tool execution (no background polling needed)
 * - Commands block until completion and return results directly
 * - Uses Jetty 12 with Jakarta EE 10 Servlet API
 * - SSE transport for MCP communication
 */
class McpServerImpl(
    private val context: Context,
) : KoinComponent {
    companion object {
        private val log = KotlinLogging.logger {}
        private const val SSE_ENDPOINT = "/sse"
        private const val MESSAGE_ENDPOINT = "/message"
    }

    private val outputHandler: OutputHandler by inject()
    private val toolRegistry = McpToolRegistry(context)
    private val jsonMapper = JacksonMcpJsonMapper(ObjectMapper())

    private var jettyServer: Server? = null
    private var mcpServer: McpSyncServer? = null

    /**
     * Start the MCP server on the specified port.
     * This method blocks until the server is stopped.
     */
    fun start(port: Int) {
        log.info { "Starting MCP server with Java SDK (version ${context.version})" }

        // Create HTTP SSE transport provider using builder
        val transportProvider =
            HttpServletSseServerTransportProvider
                .builder()
                .jsonMapper(jsonMapper)
                .sseEndpoint(SSE_ENDPOINT)
                .messageEndpoint(MESSAGE_ENDPOINT)
                .build()

        // Create sync MCP server with capabilities using builder
        val capabilities =
            McpSchema.ServerCapabilities
                .builder()
                .prompts(false) // listChanged = false
                .tools(false) // listChanged = false
                .build()

        val server =
            McpServer
                .sync(transportProvider)
                .serverInfo("easy-db-lab", context.version.toString())
                .capabilities(capabilities)
                .build()

        mcpServer = server

        // Register tools and prompts
        registerTools(server)
        registerPrompts(server)

        // Setup and start Jetty
        jettyServer = createJettyServer(port, transportProvider)

        displayStartupMessage(port)

        try {
            jettyServer?.start()
            jettyServer?.join()
        } finally {
            server.close()
        }
    }

    private fun createJettyServer(
        port: Int,
        transportProvider: HttpServletSseServerTransportProvider,
    ): Server {
        val server = Server(port)

        val contextHandler = ServletContextHandler()
        contextHandler.contextPath = "/"

        // Register the MCP transport as a servlet
        val servletHolder = ServletHolder(transportProvider)
        contextHandler.addServlet(servletHolder, "/*")

        server.handler = contextHandler
        return server
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

    private fun displayStartupMessage(port: Int) {
        outputHandler.handleMessage(
            """
            Starting MCP server on port $port...

            Server is now available at: http://127.0.0.1:$port$SSE_ENDPOINT

            Commands execute synchronously - no polling required!
            """.trimIndent(),
        )
    }

    fun stop() {
        jettyServer?.let { server ->
            if (server.isRunning) {
                server.stop()
            }
        }
        mcpServer?.close()
    }
}
