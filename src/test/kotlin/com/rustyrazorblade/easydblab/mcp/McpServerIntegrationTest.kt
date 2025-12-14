package com.rustyrazorblade.easydblab.mcp

import com.rustyrazorblade.easydblab.BaseKoinTest
import io.modelcontextprotocol.client.McpClient
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport
import io.modelcontextprotocol.spec.McpSchema
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.ServerSocket
import java.net.Socket
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Integration tests for the MCP server using Streamable HTTP transport.
 *
 * These tests start an actual MCP server and verify that:
 * 1. The server starts and accepts connections
 * 2. The list of tools matches the @McpCommand annotated commands
 */
class McpServerIntegrationTest : BaseKoinTest() {
    private var server: McpServerImpl? = null
    private var serverPort: Int = 0
    private val executor = Executors.newSingleThreadExecutor()

    @BeforeEach
    fun startServer() {
        // Find an available port
        serverPort = findAvailablePort()

        // Start server in background thread
        server = McpServerImpl(context)
        executor.submit {
            try {
                server?.start(serverPort)
            } catch (e: Exception) {
                // Server stopped or failed
            }
        }

        // Wait for server to be accepting connections
        waitForServerReady(serverPort, timeoutMs = 10000)
    }

    @AfterEach
    fun stopServer() {
        server?.stop()
        executor.shutdownNow()
        executor.awaitTermination(5, TimeUnit.SECONDS)
    }

    /**
     * Wait for the server to start accepting connections on the given port.
     */
    @Suppress("SameParameterValue")
    private fun waitForServerReady(
        port: Int,
        timeoutMs: Long,
    ) {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            try {
                Socket("127.0.0.1", port).use {
                    return // Server is ready
                }
            } catch (e: Exception) {
                Thread.sleep(100)
            }
        }
        error("Server did not start within ${timeoutMs}ms")
    }

    @Test
    fun `server should expose all McpCommand annotated commands as tools`() {
        // Get expected tool names from McpCommandDiscovery
        val expectedToolNames =
            McpCommandDiscovery
                .discoverMcpCommands(context)
                .map { it.toolName }
                .sorted()

        // Connect to server and list tools using Streamable HTTP transport
        val transport =
            HttpClientStreamableHttpTransport
                .builder("http://127.0.0.1:$serverPort/mcp")
                .build()

        val client =
            McpClient
                .sync(transport)
                .requestTimeout(Duration.ofSeconds(10))
                .capabilities(
                    McpSchema.ClientCapabilities
                        .builder()
                        .build(),
                ).build()

        try {
            // Initialize the connection
            client.initialize()

            // List tools
            val toolsResult = client.listTools()
            val actualToolNames =
                toolsResult
                    .tools()
                    .map { it.name() }
                    .sorted()

            // Verify all expected tools are present
            assertThat(actualToolNames)
                .describedAs("MCP server tools should match @McpCommand annotated commands")
                .containsExactlyElementsOf(expectedToolNames)

            // Log the tools for visibility
            println("Found ${actualToolNames.size} tools:")
            actualToolNames.forEach { println("  - $it") }
        } finally {
            client.close()
        }
    }

    @Test
    fun `server should have tools with descriptions`() {
        val transport =
            HttpClientStreamableHttpTransport
                .builder("http://127.0.0.1:$serverPort/mcp")
                .build()

        val client =
            McpClient
                .sync(transport)
                .requestTimeout(Duration.ofSeconds(10))
                .capabilities(
                    McpSchema.ClientCapabilities
                        .builder()
                        .build(),
                ).build()

        try {
            client.initialize()

            val toolsResult = client.listTools()

            // Verify all tools have descriptions
            toolsResult.tools().forEach { tool ->
                assertThat(tool.description())
                    .describedAs("Tool '${tool.name()}' should have a description")
                    .isNotBlank()
            }
        } finally {
            client.close()
        }
    }

    @Test
    fun `server should have tools with input schemas`() {
        val transport =
            HttpClientStreamableHttpTransport
                .builder("http://127.0.0.1:$serverPort/mcp")
                .build()

        val client =
            McpClient
                .sync(transport)
                .requestTimeout(Duration.ofSeconds(10))
                .capabilities(
                    McpSchema.ClientCapabilities
                        .builder()
                        .build(),
                ).build()

        try {
            client.initialize()

            val toolsResult = client.listTools()

            // Verify all tools have input schemas
            toolsResult.tools().forEach { tool ->
                assertThat(tool.inputSchema())
                    .describedAs("Tool '${tool.name()}' should have an input schema")
                    .isNotNull
            }
        } finally {
            client.close()
        }
    }

    private fun findAvailablePort(): Int {
        ServerSocket(0).use { socket ->
            return socket.localPort
        }
    }
}
