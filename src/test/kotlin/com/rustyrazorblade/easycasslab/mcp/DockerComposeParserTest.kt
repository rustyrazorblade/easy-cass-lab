package com.rustyrazorblade.easycasslab.mcp

import com.rustyrazorblade.easycasslab.BaseKoinTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class DockerComposeParserTest : BaseKoinTest() {
    @Test
    fun `should parse MCP service with port from healthcheck`(@TempDir tempDir: File) {
        // Given
        val dockerComposeContent = """
            services:
              easy-cass-mcp:
                image: rustyrazorblade/easy-cass-mcp:latest
                container_name: easy-cass-mcp
                environment:
                  - CASSANDRA_HOST=${'$'}{CASSANDRA_HOST}
                  - CASSANDRA_DATACENTER=${'$'}{CASSANDRA_DATACENTER}
                restart: unless-stopped
                network_mode: host
                healthcheck:
                  test: ["CMD", "bash", "-c", "exec 6<> /dev/tcp/localhost/8000"]
                  interval: 30s
                  timeout: 10s
                  retries: 3
                  start_period: 40s

              opensearch:
                image: opensearchproject/opensearch:latest
                container_name: opensearch
        """.trimIndent()

        val dockerComposeFile = File(tempDir, "docker-compose.yaml")
        dockerComposeFile.writeText(dockerComposeContent)

        val parser = DockerComposeParser()

        // When
        val result = parser.parseMcpService(dockerComposeFile)

        // Then
        assertThat(result).isNotNull
        assertThat(result!!.serviceName).isEqualTo("easy-cass-mcp")
        assertThat(result.port).isEqualTo(8000)
        assertThat(result.healthcheckUrl).isEqualTo("/dev/tcp/localhost/8000")
    }

    @Test
    fun `should return null when MCP service not found`(@TempDir tempDir: File) {
        // Given
        val dockerComposeContent = """
            services:
              opensearch:
                image: opensearchproject/opensearch:latest
                container_name: opensearch

              data-prepper:
                image: opensearchproject/data-prepper:latest
                container_name: data-prepper
        """.trimIndent()

        val dockerComposeFile = File(tempDir, "docker-compose.yaml")
        dockerComposeFile.writeText(dockerComposeContent)

        val parser = DockerComposeParser()

        // When
        val result = parser.parseMcpService(dockerComposeFile)

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `should return null when file does not exist`(@TempDir tempDir: File) {
        // Given
        val nonExistentFile = File(tempDir, "nonexistent.yaml")
        val parser = DockerComposeParser()

        // When
        val result = parser.parseMcpService(nonExistentFile)

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `should handle malformed YAML gracefully`(@TempDir tempDir: File) {
        // Given
        val dockerComposeContent = """
            services:
              easy-cass-mcp:
                image: rustyrazorblade/easy-cass-mcp:latest
                container_name: easy-cass-mcp
                healthcheck:
                  - this is not valid yaml structure
                  test: [missing closing bracket
        """.trimIndent()

        val dockerComposeFile = File(tempDir, "docker-compose.yaml")
        dockerComposeFile.writeText(dockerComposeContent)

        val parser = DockerComposeParser()

        // When
        val result = parser.parseMcpService(dockerComposeFile)

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `should parse port from healthcheck with curl command`(@TempDir tempDir: File) {
        // Given
        val dockerComposeContent = """
            services:
              easy-cass-mcp:
                image: rustyrazorblade/easy-cass-mcp:latest
                container_name: easy-cass-mcp
                healthcheck:
                  test: ["CMD", "curl", "-f", "http://localhost:9999/health"]
                  interval: 30s
        """.trimIndent()

        val dockerComposeFile = File(tempDir, "docker-compose.yaml")
        dockerComposeFile.writeText(dockerComposeContent)

        val parser = DockerComposeParser()

        // When
        val result = parser.parseMcpService(dockerComposeFile)

        // Then
        assertThat(result).isNotNull
        assertThat(result!!.port).isEqualTo(9999)
        assertThat(result.healthcheckUrl).isEqualTo("http://localhost:9999/health")
    }

    @Test
    fun `should use default port when healthcheck does not contain port`(@TempDir tempDir: File) {
        // Given
        val dockerComposeContent = """
            services:
              easy-cass-mcp:
                image: rustyrazorblade/easy-cass-mcp:latest
                container_name: easy-cass-mcp
                healthcheck:
                  test: ["CMD", "echo", "healthy"]
                  interval: 30s
        """.trimIndent()

        val dockerComposeFile = File(tempDir, "docker-compose.yaml")
        dockerComposeFile.writeText(dockerComposeContent)

        val parser = DockerComposeParser()

        // When
        val result = parser.parseMcpService(dockerComposeFile)

        // Then
        assertThat(result).isNotNull
        assertThat(result!!.port).isEqualTo(8000) // Default port
        assertThat(result.healthcheckUrl).isNull()
    }
}
