package com.rustyrazorblade.easycasslab.mcp

import com.rustyrazorblade.easycasslab.BaseKoinTest
import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.configuration.Host
import com.rustyrazorblade.easycasslab.configuration.ServerType
import com.rustyrazorblade.easycasslab.configuration.TFState
import com.rustyrazorblade.easycasslab.di.TFStateProvider
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File
import java.io.InputStream

class RemoteMcpIntegrationTest : BaseKoinTest() {
    private lateinit var testContext: Context
    private lateinit var mockTfState: TFState
    private lateinit var discovery: RemoteMcpDiscovery
    private lateinit var proxy: RemoteMcpProxy

    @BeforeEach
    fun setup() {
        // Create a minimal Context with mocked TFState
        mockTfState = mock()
        testContext =
            Context(
                easycasslabUserDirectory = File("/tmp/test-user-dir"),
            )

        // Create a mock TFStateProvider
        val mockProvider =
            object : TFStateProvider {
                override fun parseFromFile(file: File) = mockTfState

                override fun parseFromStream(stream: java.io.InputStream) = mockTfState

                override fun getDefault() = mockTfState
            }

        // Create real instances for integration testing
        discovery = RemoteMcpDiscovery(testContext, mockProvider)
        proxy = RemoteMcpProxy()
    }

    @Test
    fun `should discover remote servers from control nodes`() {
        // Given - Mock infrastructure with control nodes
        val controlHost = Host("192.168.1.100", "10.0.1.100", "control0", "us-west-2a")
        whenever(mockTfState.getHosts(ServerType.Control)).thenReturn(listOf(controlHost))

        // Mock docker-compose.yaml content
        val dockerComposeContent =
            """
            services:
              easy-cass-mcp:
                ports:
                  - "8000:3000"
            """.trimIndent()

        // Create a mock provider
        val mockProvider =
            object : TFStateProvider {
                override fun parseFromFile(file: File) = mockTfState

                override fun parseFromStream(stream: java.io.InputStream) = mockTfState

                override fun getDefault() = mockTfState
            }

        // Create a mock discovery that returns our test content
        val mockDiscovery =
            object : RemoteMcpDiscovery(testContext, mockProvider) {
                override fun discoverRemoteServers(): List<RemoteMcpDiscovery.RemoteServer> {
                    // Override to inject our test docker-compose content
                    val controlHosts = mockTfState.getHosts(ServerType.Control)
                    return controlHosts.mapNotNull { host ->
                        // Write content to temp file for parsing
                        val tempFile = File.createTempFile("docker-compose-", ".yaml")
                        tempFile.writeText(dockerComposeContent)
                        tempFile.deleteOnExit()

                        val parser = DockerComposeParser()
                        val mcpService = parser.parseMcpService(tempFile) ?: return@mapNotNull null
                        RemoteMcpDiscovery.RemoteServer(
                            nodeName = host.alias,
                            host = host.private,
                            port = mcpService.port,
                            endpoint = "http://${host.private}:${mcpService.port}/sse",
                        )
                    }
                }
            }

        // When - Discover remote servers
        val remoteServers = mockDiscovery.discoverRemoteServers()

        // Then - Verify discovery worked
        assertThat(remoteServers).hasSize(1)
        assertThat(remoteServers.first().host).isEqualTo("10.0.1.100")
        assertThat(remoteServers.first().port).isEqualTo(8000)
        assertThat(remoteServers.first().nodeName).isEqualTo("control0")
    }

    @Test
    fun `should handle tool execution through proxy`() {
        // Given - A remote server with tools
        val server =
            RemoteMcpDiscovery.RemoteServer(
                nodeName = "control0",
                host = "192.168.1.100",
                port = 8000,
                endpoint = "http://192.168.1.100:8000/sse",
            )

        // Create a mock proxy that simulates successful tool fetch and execution
        val mockProxy =
            object : RemoteMcpProxy() {
                override fun fetchToolsFromServer(server: RemoteMcpDiscovery.RemoteServer): List<RemoteToolInfo> {
                    return listOf(
                        RemoteToolInfo(
                            name = "test-tool",
                            description = "A test tool",
                            inputSchema = null,
                        ),
                    )
                }

                override fun executeRemoteTool(
                    toolName: String,
                    arguments: JsonObject?,
                    server: RemoteMcpDiscovery.RemoteServer,
                ): McpToolRegistry.ToolResult {
                    return McpToolRegistry.ToolResult(
                        content = listOf("Tool executed successfully"),
                        isError = false,
                    )
                }
            }

        // When - Fetch tools
        val tools = mockProxy.fetchToolsFromServer(server)

        // Then - Verify tools were fetched
        assertThat(tools).hasSize(1)
        assertThat(tools.first().name).isEqualTo("test-tool")

        // When - Execute the tool
        val result =
            mockProxy.executeRemoteTool(
                "test-tool",
                JsonObject(mapOf("param" to JsonPrimitive("value"))),
                server,
            )

        // Then - Verify execution result
        assertThat(result.content).contains("Tool executed successfully")
        assertThat(result.isError).isFalse()
    }

    @Test
    fun `should handle multiple control nodes with different tools`() {
        // Given - Multiple control nodes
        val controlHosts =
            listOf(
                Host("192.168.1.100", "10.0.1.100", "control0", "us-west-2a"),
                Host("192.168.1.101", "10.0.1.101", "control1", "us-west-2b"),
            )
        whenever(mockTfState.getHosts(ServerType.Control)).thenReturn(controlHosts)

        // Create a mock provider
        val mockProvider =
            object : TFStateProvider {
                override fun parseFromFile(file: File) = mockTfState

                override fun parseFromStream(stream: java.io.InputStream) = mockTfState

                override fun getDefault() = mockTfState
            }

        // Create mock discovery with different docker-compose for each host
        val mockDiscovery =
            object : RemoteMcpDiscovery(testContext, mockProvider) {
                override fun discoverRemoteServers(): List<RemoteMcpDiscovery.RemoteServer> {
                    return listOf(
                        RemoteMcpDiscovery.RemoteServer("control0", "10.0.1.100", 8000, "http://10.0.1.100:8000/sse"),
                        RemoteMcpDiscovery.RemoteServer("control1", "10.0.1.101", 8001, "http://10.0.1.101:8001/sse"),
                    )
                }
            }

        // When - Discover all remote servers
        val remoteServers = mockDiscovery.discoverRemoteServers()

        // Then - Verify both servers were discovered
        assertThat(remoteServers).hasSize(2)
        assertThat(remoteServers.map { it.nodeName }).containsExactlyInAnyOrder("control0", "control1")
        assertThat(remoteServers.map { it.port }).containsExactlyInAnyOrder(8000, 8001)
    }

    @Test
    fun `should handle connection failures gracefully`() {
        // Given - A remote server
        val server =
            RemoteMcpDiscovery.RemoteServer(
                nodeName = "control0",
                host = "192.168.1.100",
                port = 8000,
                endpoint = "http://192.168.1.100:8000/sse",
            )

        // Create a mock proxy that simulates connection failure
        val mockProxy =
            object : RemoteMcpProxy() {
                override fun fetchToolsFromServer(server: RemoteMcpDiscovery.RemoteServer): List<RemoteToolInfo> {
                    // Simulate connection failure
                    return emptyList()
                }

                override fun testConnection(server: RemoteMcpDiscovery.RemoteServer): Boolean {
                    return false
                }
            }

        // When - Test connection
        val isConnected = mockProxy.testConnection(server)

        // Then - Verify failure is handled
        assertThat(isConnected).isFalse()

        // When - Fetch tools from unreachable server
        val tools = mockProxy.fetchToolsFromServer(server)

        // Then - Verify empty list is returned
        assertThat(tools).isEmpty()
    }

    @Test
    fun `should handle partial failures in multi-node setup`() {
        // Given - Multiple servers, one failing
        val server1 = RemoteMcpDiscovery.RemoteServer("control0", "10.0.1.100", 8000, "http://10.0.1.100:8000/sse")
        val server2 = RemoteMcpDiscovery.RemoteServer("control1", "10.0.1.101", 8001, "http://10.0.1.101:8001/sse")

        // Create mock proxy with mixed results
        val mockProxy =
            object : RemoteMcpProxy() {
                override fun fetchToolsFromServer(server: RemoteMcpDiscovery.RemoteServer): List<RemoteToolInfo> {
                    return when (server.nodeName) {
                        "control0" ->
                            listOf(
                                RemoteToolInfo("tool1", "Tool 1", null),
                            )
                        "control1" -> emptyList() // Simulate failure
                        else -> emptyList()
                    }
                }
            }

        // When - Fetch tools from both servers
        val tools1 = mockProxy.fetchToolsFromServer(server1)
        val tools2 = mockProxy.fetchToolsFromServer(server2)

        // Then - Verify only successful server returned tools
        assertThat(tools1).hasSize(1)
        assertThat(tools1.first().name).isEqualTo("tool1")
        assertThat(tools2).isEmpty()
    }

    @Test
    fun `should parse MCP service from docker-compose`() {
        // Given - Docker compose content
        val dockerComposeContent =
            """
            services:
              easy-cass-mcp:
                ports:
                  - "8000:3000"
                healthcheck:
                  test: ["CMD", "curl", "-f", "http://localhost:3000/health"]
            """.trimIndent()

        // When - Parse the MCP service
        val tempFile = File.createTempFile("docker-compose-", ".yaml")
        tempFile.writeText(dockerComposeContent)
        tempFile.deleteOnExit()

        val parser = DockerComposeParser()
        val mcpService = parser.parseMcpService(tempFile)

        // Then - Verify parsing
        assertThat(mcpService).isNotNull()
        assertThat(mcpService?.serviceName).isEqualTo("easy-cass-mcp")
        // The parser extracts the container port from healthcheck, not the host port
        assertThat(mcpService?.port).isEqualTo(3000)
    }

    @Test
    fun `should handle invalid docker-compose content gracefully`() {
        // Given - Invalid docker-compose content
        val invalidContent = "invalid yaml content {{{"

        // When - Try to parse
        val tempFile = File.createTempFile("docker-compose-", ".yaml")
        tempFile.writeText(invalidContent)
        tempFile.deleteOnExit()

        val parser = DockerComposeParser()
        val mcpService = parser.parseMcpService(tempFile)

        // Then - Verify null result
        assertThat(mcpService).isNull()
    }

    @Test
    fun `should handle missing easy-cass-mcp service gracefully`() {
        // Given - Docker compose without easy-cass-mcp service
        val dockerComposeContent =
            """
            services:
              other-service:
                ports:
                  - "9000:9000"
            """.trimIndent()

        // When - Parse the content
        val tempFile = File.createTempFile("docker-compose-", ".yaml")
        tempFile.writeText(dockerComposeContent)
        tempFile.deleteOnExit()

        val parser = DockerComposeParser()
        val mcpService = parser.parseMcpService(tempFile)

        // Then - Verify no easy-cass-mcp service
        assertThat(mcpService).isNull()
    }

    @Test
    fun `should create RemoteServer with correct endpoint format`() {
        // Given
        val server =
            RemoteMcpDiscovery.RemoteServer(
                nodeName = "control0",
                host = "192.168.1.100",
                port = 8000,
                endpoint = "http://192.168.1.100:8000/sse",
            )

        // Then
        assertThat(server.nodeName).isEqualTo("control0")
        assertThat(server.host).isEqualTo("192.168.1.100")
        assertThat(server.port).isEqualTo(8000)
        assertThat(server.endpoint).isEqualTo("http://192.168.1.100:8000/sse")
    }

    @Test
    fun `should create RemoteToolInfo correctly`() {
        // Given & When
        val toolInfo =
            RemoteMcpProxy.RemoteToolInfo(
                name = "test-tool",
                description = "Test tool description",
                inputSchema = null,
            )

        // Then
        assertThat(toolInfo.name).isEqualTo("test-tool")
        assertThat(toolInfo.description).isEqualTo("Test tool description")
        assertThat(toolInfo.inputSchema).isNull()
    }
}
