package com.rustyrazorblade.easycasslab.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.File

/**
 * Parser for docker-compose.yaml files to extract MCP service configuration.
 * This is used to discover the port configuration for remote MCP servers running
 * on control nodes.
 */
class DockerComposeParser {
    companion object {
        private val log = KotlinLogging.logger {}
        private const val MCP_SERVICE_NAME = "easy-cass-mcp"
        private const val DEFAULT_MCP_PORT = 8000
    }

    /**
     * Information about an MCP service extracted from docker-compose.yaml
     */
    data class McpServiceInfo(
        val serviceName: String,
        val port: Int,
        val healthcheckUrl: String? = null,
    )

    private val yamlMapper = ObjectMapper(YAMLFactory())

    /**
     * Parse a docker-compose.yaml file to extract MCP service configuration.
     *
     * @param file The docker-compose.yaml file to parse
     * @return McpServiceInfo if the MCP service is found, null otherwise
     */
    fun parseMcpService(file: File): McpServiceInfo? {
        if (!file.exists()) {
            log.debug { "Docker compose file does not exist: ${file.absolutePath}" }
            return null
        }

        return try {
            val root = yamlMapper.readTree(file)
            val services = root["services"] ?: return null

            // Look for the MCP service
            val mcpService = services[MCP_SERVICE_NAME] ?: return null

            // Extract port from healthcheck
            val healthcheck = mcpService["healthcheck"]
            val port = extractPortFromHealthcheck(healthcheck)
            val healthcheckUrl = extractHealthcheckUrl(healthcheck)

            McpServiceInfo(
                serviceName = MCP_SERVICE_NAME,
                port = port,
                healthcheckUrl = healthcheckUrl,
            )
        } catch (e: Exception) {
            log.warn { "Failed to parse docker-compose.yaml: ${e.message}" }
            null
        }
    }

    /**
     * Extract port number from healthcheck configuration.
     * Looks for patterns like:
     * - /dev/tcp/localhost/8000
     * - http://localhost:9999/health
     */
    private fun extractPortFromHealthcheck(healthcheck: com.fasterxml.jackson.databind.JsonNode?): Int {
        if (healthcheck == null) {
            return DEFAULT_MCP_PORT
        }

        val test = healthcheck["test"] ?: return DEFAULT_MCP_PORT

        // Convert test array to string for pattern matching
        val testString = when {
            test.isArray -> test.joinToString(" ") { it.asText() }
            test.isTextual -> test.asText()
            else -> return DEFAULT_MCP_PORT
        }

        // Try to extract port from various patterns
        val patterns = listOf(
            // Pattern for /dev/tcp/localhost/8000
            Regex("""/dev/tcp/localhost/(\d+)"""),
            // Pattern for http://localhost:9999/health
            Regex("""http://localhost:(\d+)"""),
            // Pattern for 127.0.0.1:8000
            Regex("""127\.0\.0\.1:(\d+)"""),
            // Pattern for 0.0.0.0:8000
            Regex("""0\.0\.0\.0:(\d+)"""),
        )

        for (pattern in patterns) {
            val match = pattern.find(testString)
            if (match != null) {
                return match.groupValues[1].toIntOrNull() ?: DEFAULT_MCP_PORT
            }
        }

        return DEFAULT_MCP_PORT
    }

    /**
     * Extract the full healthcheck URL from the healthcheck configuration.
     */
    private fun extractHealthcheckUrl(healthcheck: com.fasterxml.jackson.databind.JsonNode?): String? {
        if (healthcheck == null) {
            return null
        }

        val test = healthcheck["test"] ?: return null

        // Convert test array to string for pattern matching
        val testString = when {
            test.isArray -> test.joinToString(" ") { it.asText() }
            test.isTextual -> test.asText()
            else -> return null
        }

        // Look for URL patterns
        val urlPatterns = listOf(
            // Pattern for /dev/tcp/localhost/8000
            Regex("""/dev/tcp/localhost/\d+"""),
            // Pattern for http://localhost:9999/health
            Regex("""http://localhost:\d+[/\w-]*"""),
        )

        for (pattern in urlPatterns) {
            val match = pattern.find(testString)
            if (match != null) {
                return match.value
            }
        }

        return null
    }
}
