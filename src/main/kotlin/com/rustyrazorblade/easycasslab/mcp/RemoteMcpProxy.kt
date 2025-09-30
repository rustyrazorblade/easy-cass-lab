package com.rustyrazorblade.easycasslab.mcp

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.jackson.jackson
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.net.ConnectException
import java.time.Duration

/**
 * Handles actual communication with remote MCP servers using HTTP/SSE over SSH tunnels.
 * This class is responsible for:
 * 1. Establishing connections to remote MCP servers through SSH tunnels
 * 2. Fetching available tools from remote servers
 * 3. Forwarding tool execution requests to remote servers
 * 4. Handling SSE streaming responses
 */
open class RemoteMcpProxy : KoinComponent {
    companion object {
        private val log = KotlinLogging.logger {}
        private const val CONNECTION_TIMEOUT_SECONDS = 10L
        private const val REQUEST_TIMEOUT_SECONDS = 30L
        private const val TOOLS_ENDPOINT = "/tools/list"
        private const val EXECUTE_ENDPOINT = "/tools/call"
    }

    private val tunnelManager: SshTunnelManager by inject()

    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            jackson {
                // Configure Jackson if needed
            }
        }
        install(HttpTimeout) {
            connectTimeoutMillis = Duration.ofSeconds(CONNECTION_TIMEOUT_SECONDS).toMillis()
            requestTimeoutMillis = Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS).toMillis()
        }
    }

    private val objectMapper = ObjectMapper()

    /**
     * Get the tunneled URL for a remote server.
     * If SSH tunneling is enabled, returns localhost URL with the tunneled port.
     * Otherwise, returns the direct URL to the remote server.
     */
    private fun getTunneledUrl(server: RemoteMcpDiscovery.RemoteServer, endpoint: String): String {
        val localPort = tunnelManager.getLocalPort(server.nodeName)
        if (localPort != null) {
            log.debug { "Using SSH tunnel for ${server.nodeName}: localhost:$localPort" }
            return "http://localhost:$localPort$endpoint"
        }

        // Fallback to direct connection if no tunnel is available
        log.debug { "Using direct connection for ${server.nodeName}: ${server.host}:${server.port}" }
        return "http://${server.host}:${server.port}$endpoint"
    }

    /**
     * Fetch the list of available tools from a remote MCP server.
     *
     * @param server The remote server to query
     * @return List of tool information from the remote server
     */
    open fun fetchToolsFromServer(server: RemoteMcpDiscovery.RemoteServer): List<RemoteToolInfo> {
        log.info { "Fetching tools from remote server ${server.nodeName}" }

        return runBlocking {
            try {
                val url = getTunneledUrl(server, TOOLS_ENDPOINT)
                val response: HttpResponse = httpClient.get(url)

                if (response.status == HttpStatusCode.OK) {
                    val responseBody = response.bodyAsText()
                    val toolsResponse = objectMapper.readTree(responseBody)
                    val tools = toolsResponse.get("tools") ?: return@runBlocking emptyList()

                    tools.mapNotNull { toolNode ->
                        try {
                            RemoteToolInfo(
                                name = toolNode.get("name")?.asText() ?: return@mapNotNull null,
                                description = toolNode.get("description")?.asText() ?: "",
                                inputSchema = toolNode.get("inputSchema")
                            )
                        } catch (e: Exception) {
                            log.warn { "Failed to parse tool from ${server.nodeName}: ${e.message}" }
                            null
                        }
                    }
                } else {
                    log.warn { "Failed to fetch tools from ${server.nodeName}: HTTP ${response.status}" }
                    emptyList()
                }
            } catch (e: ConnectException) {
                log.warn { "Failed to connect to ${server.nodeName}: ${e.message}" }
                emptyList()
            } catch (e: Exception) {
                log.error(e) { "Unexpected error fetching tools from ${server.nodeName}" }
                emptyList()
            }
        }
    }

    /**
     * Execute a tool on a remote MCP server.
     *
     * @param toolName The name of the tool to execute
     * @param arguments The arguments to pass to the tool
     * @param server The remote server to execute the tool on
     * @return The result from the remote tool execution
     */
    open fun executeRemoteTool(
        toolName: String,
        arguments: JsonObject?,
        server: RemoteMcpDiscovery.RemoteServer
    ): McpToolRegistry.ToolResult {
        log.info { "Executing remote tool '$toolName' on ${server.nodeName}" }

        return runBlocking {
            try {
                val url = getTunneledUrl(server, EXECUTE_ENDPOINT)

                // Prepare the request body
                val requestBody = objectMapper.createObjectNode().apply {
                    put("name", toolName)
                    if (arguments != null) {
                        set<ObjectNode>("arguments", objectMapper.readTree(arguments.toString()))
                    }
                }

                val response: HttpResponse = httpClient.post(url) {
                    contentType(ContentType.Application.Json)
                    setBody(requestBody.toString())
                }

                if (response.status == HttpStatusCode.OK) {
                    val responseBody = response.bodyAsText()
                    val resultNode = objectMapper.readTree(responseBody)

                    // Parse the response and create a ToolResult
                    val content = resultNode.get("content")?.asText() ?: "Tool executed successfully"
                    val isError = resultNode.get("isError")?.asBoolean() ?: false

                    McpToolRegistry.ToolResult(
                        content = listOf(content),
                        isError = isError
                    )
                } else {
                    val errorMessage = "Failed to execute tool on ${server.nodeName}: HTTP ${response.status}"
                    log.warn { errorMessage }
                    McpToolRegistry.ToolResult(
                        content = listOf(errorMessage),
                        isError = true
                    )
                }
            } catch (e: ConnectException) {
                val errorMessage = "Failed to connect to ${server.nodeName}: ${e.message}"
                log.warn { errorMessage }
                McpToolRegistry.ToolResult(
                    content = listOf(errorMessage),
                    isError = true
                )
            } catch (e: Exception) {
                val errorMessage = "Unexpected error executing tool on ${server.nodeName}: ${e.message}"
                log.error(e) { errorMessage }
                McpToolRegistry.ToolResult(
                    content = listOf(errorMessage),
                    isError = true
                )
            }
        }
    }

    /**
     * Test connectivity to a remote MCP server.
     *
     * @param server The remote server to test
     * @return true if the server is reachable and responding, false otherwise
     */
    open fun testConnection(server: RemoteMcpDiscovery.RemoteServer): Boolean {
        return runBlocking {
            try {
                val url = getTunneledUrl(server, "/health")
                val response: HttpResponse = httpClient.get(url)
                response.status == HttpStatusCode.OK
            } catch (e: Exception) {
                log.debug { "Connection test failed for ${server.nodeName}: ${e.message}" }
                false
            }
        }
    }

    /**
     * Close the HTTP client and release resources.
     */
    fun close() {
        httpClient.close()
    }

    /**
     * Data class representing tool information from a remote MCP server.
     */
    data class RemoteToolInfo(
        val name: String,
        val description: String,
        val inputSchema: JsonNode? = null
    )
}
