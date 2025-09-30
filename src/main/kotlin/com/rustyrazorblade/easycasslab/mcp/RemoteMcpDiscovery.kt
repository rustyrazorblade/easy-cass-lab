package com.rustyrazorblade.easycasslab.mcp

import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.configuration.ServerType
import com.rustyrazorblade.easycasslab.di.TFStateProvider
import io.github.oshai.kotlinlogging.KotlinLogging
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Discovers remote MCP servers running on control nodes.
 * This class is responsible for:
 * 1. Reading the docker-compose.yaml to find MCP service configuration
 * 2. Getting control node IPs from TFState
 * 3. Verifying connectivity to remote MCP servers
 * 4. Returning a list of available remote servers
 */
open class RemoteMcpDiscovery(
    private val context: Context,
    private val tfStateProvider: TFStateProvider? = null,
) : KoinComponent {
    companion object {
        private val log = KotlinLogging.logger {}
        private const val HEALTH_CHECK_TIMEOUT_SECONDS = 5L
        private const val HEALTH_CHECK_PATH = "/health"
        private const val SSE_PATH = "/sse"
    }

    /**
     * Information about a remote MCP server
     */
    data class RemoteServer(
        val nodeName: String,
        val host: String,
        val port: Int,
        val endpoint: String,
        // Tunneled connection info (populated after SSH tunnel is established)
        var tunneledHost: String? = null,
        var tunneledPort: Int? = null,
        var tunneledEndpoint: String? = null
    ) {
        /**
         * Gets the effective endpoint to use (tunneled if available, direct otherwise).
         */
        fun getEffectiveEndpoint(): String {
            return tunneledEndpoint ?: endpoint
        }

        /**
         * Gets the effective host to use (tunneled if available, direct otherwise).
         */
        fun getEffectiveHost(): String {
            return tunneledHost ?: host
        }

        /**
         * Gets the effective port to use (tunneled if available, direct otherwise).
         */
        fun getEffectivePort(): Int {
            return tunneledPort ?: port
        }

        /**
         * Updates tunnel information after SSH tunnel is established.
         */
        fun updateTunnelInfo(localPort: Int) {
            tunneledHost = "localhost"
            tunneledPort = localPort
            tunneledEndpoint = "http://localhost:$localPort/sse"
        }
    }

    // Inject TFStateProvider if not provided in constructor
    private val tfState by lazy {
        val provider = tfStateProvider ?: inject<TFStateProvider>().value
        provider.getDefault()
    }

    /**
     * Discover all available remote MCP servers on control nodes.
     *
     * @return List of available remote MCP servers
     */
    open fun discoverRemoteServers(): List<RemoteServer> {
        log.info { "Starting remote MCP server discovery" }

        // Parse docker-compose.yaml to get MCP service configuration
        val dockerComposeFile = File(context.easycasslabUserDirectory, "control/docker-compose.yaml")
        if (!dockerComposeFile.exists()) {
            log.debug { "Docker compose file not found at ${dockerComposeFile.absolutePath}" }
            return emptyList()
        }

        val parser = DockerComposeParser()
        val mcpServiceInfo = parser.parseMcpService(dockerComposeFile)
        if (mcpServiceInfo == null) {
            log.warn { "MCP service not found in docker-compose.yaml" }
            return emptyList()
        }

        log.info { "Found MCP service '${mcpServiceInfo.serviceName}' on port ${mcpServiceInfo.port}" }

        // Get control node IPs from TFState
        val controlNodes = tfState.getHosts(ServerType.Control)
        if (controlNodes.isEmpty()) {
            log.info { "No control nodes found in TFState" }
            return emptyList()
        }

        log.info { "Found ${controlNodes.size} control nodes, checking connectivity..." }

        // Check connectivity to each control node's MCP server
        val availableServers = mutableListOf<RemoteServer>()
        val httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(HEALTH_CHECK_TIMEOUT_SECONDS))
            .build()

        for (node in controlNodes) {
            val healthCheckUrl = "http://${node.private}:${mcpServiceInfo.port}$HEALTH_CHECK_PATH"
            log.debug { "Checking MCP server health at $healthCheckUrl" }

            if (isServerHealthy(httpClient, healthCheckUrl)) {
                val server = RemoteServer(
                    nodeName = node.alias,
                    host = node.private,
                    port = mcpServiceInfo.port,
                    endpoint = "http://${node.private}:${mcpServiceInfo.port}$SSE_PATH",
                )
                availableServers.add(server)
                log.info { "MCP server available on ${node.alias} at ${server.endpoint}" }
            } else {
                log.warn { "MCP server on ${node.alias} is not reachable or unhealthy" }
            }
        }

        log.info { "Discovery complete. Found ${availableServers.size} available MCP servers" }
        return availableServers
    }

    /**
     * Check if a remote MCP server is healthy and reachable.
     *
     * @param httpClient HTTP client to use for the health check
     * @param healthCheckUrl URL of the health check endpoint
     * @return true if the server is healthy, false otherwise
     */
    private fun isServerHealthy(
        httpClient: HttpClient,
        healthCheckUrl: String,
    ): Boolean {
        return try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(healthCheckUrl))
                .timeout(Duration.ofSeconds(HEALTH_CHECK_TIMEOUT_SECONDS))
                .GET()
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            val isHealthy = response.statusCode() == 200

            if (isHealthy) {
                log.debug { "Health check successful for $healthCheckUrl" }
            } else {
                log.debug { "Health check failed for $healthCheckUrl with status ${response.statusCode()}" }
            }

            isHealthy
        } catch (e: Exception) {
            log.debug { "Health check failed for $healthCheckUrl: ${e.message}" }
            false
        }
    }
}
