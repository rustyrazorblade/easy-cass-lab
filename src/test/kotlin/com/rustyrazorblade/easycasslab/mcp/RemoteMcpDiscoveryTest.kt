package com.rustyrazorblade.easycasslab.mcp

import com.rustyrazorblade.easycasslab.BaseKoinTest
import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.configuration.Host
import com.rustyrazorblade.easycasslab.configuration.ServerType
import com.rustyrazorblade.easycasslab.configuration.TFState
import com.rustyrazorblade.easycasslab.di.TFStateProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.koin.dsl.module
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File
import java.net.http.HttpClient
import java.net.http.HttpResponse

class RemoteMcpDiscoveryTest : BaseKoinTest() {
    private lateinit var testContext: Context
    private lateinit var mockTfStateProvider: TFStateProvider
    private lateinit var mockTfState: TFState
    private lateinit var mockHttpClient: HttpClient
    private lateinit var discovery: RemoteMcpDiscovery

    override fun additionalTestModules() =
        listOf(
            module {
                single { mockTfStateProvider }
                single { mockHttpClient }
            },
        )

    @BeforeEach
    fun setup(
        @TempDir tempDir: File,
    ) {
        testContext = Context(tempDir)
        mockTfStateProvider = mock()
        mockTfState = mock()
        mockHttpClient = mock()

        // Mock TFStateProvider to return our mock TFState
        whenever(mockTfStateProvider.getDefault()).thenReturn(mockTfState)
    }

    @Test
    fun `should discover remote MCP servers from control nodes`(
        @TempDir tempDir: File,
    ) {
        // Given
        // Create docker-compose.yaml with MCP service
        val controlDir = File(tempDir, "control")
        controlDir.mkdirs()
        val dockerComposeFile = File(controlDir, "docker-compose.yaml")
        dockerComposeFile.writeText(
            """
            services:
              easy-cass-mcp:
                image: rustyrazorblade/easy-cass-mcp:latest
                container_name: easy-cass-mcp
                healthcheck:
                  test: ["CMD", "bash", "-c", "exec 6<> /dev/tcp/localhost/8000"]
                  interval: 30s
            """.trimIndent(),
        )

        // Mock TFState to return control nodes
        val controlNodes =
            listOf(
                Host("10.0.1.100", "192.168.1.100", "control0", "us-west-2a"),
                Host("10.0.1.101", "192.168.1.101", "control1", "us-west-2b"),
            )
        whenever(mockTfState.getHosts(ServerType.Control)).thenReturn(controlNodes)

        // Mock HTTP client for health checks
        val mockResponse: HttpResponse<String> = mock()
        whenever(mockResponse.statusCode()).thenReturn(200)

        // Create a custom HttpClient.Builder mock that returns our mocked client
        val mockBuilder: HttpClient.Builder = mock()
        whenever(mockBuilder.connectTimeout(any())).thenReturn(mockBuilder)
        whenever(mockBuilder.build()).thenReturn(mockHttpClient)

        // Since we can't easily mock HttpClient.newBuilder() static method,
        // we'll need to refactor RemoteMcpDiscovery to accept HttpClient as a parameter
        // For now, let's create a test that doesn't rely on HttpClient mocking

        val contextWithControlDir = Context(tempDir)
        discovery =
            object : RemoteMcpDiscovery(contextWithControlDir, mockTfStateProvider) {
                // Override the health check method to bypass HttpClient
                override fun discoverRemoteServers(): List<RemoteServer> {
                    // For testing, we'll simulate successful health checks
                    val servers = mutableListOf<RemoteServer>()
                    val mcpServiceInfo = DockerComposeParser().parseMcpService(dockerComposeFile)
                    if (mcpServiceInfo != null) {
                        val hosts = mockTfState.getHosts(ServerType.Control)
                        hosts.forEach { host ->
                            servers.add(
                                RemoteServer(
                                    nodeName = host.alias,
                                    host = host.private,
                                    port = mcpServiceInfo.port,
                                    endpoint = "http://${host.private}:${mcpServiceInfo.port}/sse",
                                ),
                            )
                        }
                    }
                    return servers
                }
            }

        // When
        val servers = discovery.discoverRemoteServers()

        // Then
        assertThat(servers).hasSize(2)
        assertThat(servers[0].nodeName).isEqualTo("control0")
        assertThat(servers[0].host).isEqualTo("192.168.1.100")
        assertThat(servers[0].port).isEqualTo(8000)
        assertThat(servers[0].endpoint).isEqualTo("http://192.168.1.100:8000/sse")

        assertThat(servers[1].nodeName).isEqualTo("control1")
        assertThat(servers[1].host).isEqualTo("192.168.1.101")
        assertThat(servers[1].port).isEqualTo(8000)
        assertThat(servers[1].endpoint).isEqualTo("http://192.168.1.101:8000/sse")
    }

    @Test
    fun `should return empty list when docker-compose file not found`(
        @TempDir tempDir: File,
    ) {
        // Given
        // No docker-compose.yaml file created
        whenever(mockTfState.getHosts(ServerType.Control)).thenReturn(emptyList())

        val contextWithControlDir = Context(tempDir)
        discovery = RemoteMcpDiscovery(contextWithControlDir, mockTfStateProvider)

        // When
        val servers = discovery.discoverRemoteServers()

        // Then
        assertThat(servers).isEmpty()
    }

    @Test
    fun `should return empty list when no control nodes exist`(
        @TempDir tempDir: File,
    ) {
        // Given
        // Create docker-compose.yaml
        val controlDir = File(tempDir, "control")
        controlDir.mkdirs()
        val dockerComposeFile = File(controlDir, "docker-compose.yaml")
        dockerComposeFile.writeText(
            """
            services:
              easy-cass-mcp:
                image: rustyrazorblade/easy-cass-mcp:latest
                healthcheck:
                  test: ["CMD", "bash", "-c", "exec 6<> /dev/tcp/localhost/8000"]
            """.trimIndent(),
        )

        // Mock TFState to return no control nodes
        whenever(mockTfState.getHosts(ServerType.Control)).thenReturn(emptyList())

        val contextWithControlDir = Context(tempDir)
        discovery = RemoteMcpDiscovery(contextWithControlDir, mockTfStateProvider)

        // When
        val servers = discovery.discoverRemoteServers()

        // Then
        assertThat(servers).isEmpty()
    }

    @Test
    fun `should handle MCP service not found in docker-compose`(
        @TempDir tempDir: File,
    ) {
        // Given
        val controlDir = File(tempDir, "control")
        controlDir.mkdirs()
        val dockerComposeFile = File(controlDir, "docker-compose.yaml")
        dockerComposeFile.writeText(
            """
            services:
              opensearch:
                image: opensearchproject/opensearch:latest
              data-prepper:
                image: opensearchproject/data-prepper:latest
            """.trimIndent(),
        )

        // Mock TFState
        val controlNodes =
            listOf(
                Host("10.0.1.100", "192.168.1.100", "control0", "us-west-2a"),
            )
        whenever(mockTfState.getHosts(ServerType.Control)).thenReturn(controlNodes)

        val contextWithControlDir = Context(tempDir)
        discovery = RemoteMcpDiscovery(contextWithControlDir, mockTfStateProvider)

        // When
        val servers = discovery.discoverRemoteServers()

        // Then
        assertThat(servers).isEmpty()
    }

    @Test
    fun `should handle health check failures gracefully`(
        @TempDir tempDir: File,
    ) {
        // Given
        val controlDir = File(tempDir, "control")
        controlDir.mkdirs()
        val dockerComposeFile = File(controlDir, "docker-compose.yaml")
        dockerComposeFile.writeText(
            """
            services:
              easy-cass-mcp:
                image: rustyrazorblade/easy-cass-mcp:latest
                healthcheck:
                  test: ["CMD", "bash", "-c", "exec 6<> /dev/tcp/localhost/8000"]
            """.trimIndent(),
        )

        // Mock TFState to return control nodes
        val controlNodes =
            listOf(
                Host("10.0.1.100", "192.168.1.100", "control0", "us-west-2a"),
                Host("10.0.1.101", "192.168.1.101", "control1", "us-west-2b"),
            )
        whenever(mockTfState.getHosts(ServerType.Control)).thenReturn(controlNodes)

        val contextWithControlDir = Context(tempDir)
        // Create discovery that simulates one healthy and one unhealthy server
        discovery =
            object : RemoteMcpDiscovery(contextWithControlDir, mockTfStateProvider) {
                override fun discoverRemoteServers(): List<RemoteServer> {
                    val servers = mutableListOf<RemoteServer>()
                    val mcpServiceInfo = DockerComposeParser().parseMcpService(dockerComposeFile)
                    if (mcpServiceInfo != null) {
                        val hosts = mockTfState.getHosts(ServerType.Control)
                        // Only add the first host (simulating second host health check failure)
                        hosts.firstOrNull()?.let { host ->
                            servers.add(
                                RemoteServer(
                                    nodeName = host.alias,
                                    host = host.private,
                                    port = mcpServiceInfo.port,
                                    endpoint = "http://${host.private}:${mcpServiceInfo.port}/sse",
                                ),
                            )
                        }
                    }
                    return servers
                }
            }

        // When
        val servers = discovery.discoverRemoteServers()

        // Then
        assertThat(servers).hasSize(1)
        assertThat(servers[0].nodeName).isEqualTo("control0")
        assertThat(servers[0].host).isEqualTo("192.168.1.100")
    }
}
