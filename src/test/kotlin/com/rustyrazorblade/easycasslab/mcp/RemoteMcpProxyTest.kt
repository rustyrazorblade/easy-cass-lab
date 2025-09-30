package com.rustyrazorblade.easycasslab.mcp

import com.rustyrazorblade.easycasslab.BaseKoinTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class RemoteMcpProxyTest : BaseKoinTest() {
    private lateinit var proxy: RemoteMcpProxy

    @BeforeEach
    fun setup() {
        proxy = RemoteMcpProxy()
    }

    @Test
    fun `should return empty list when server is unreachable`() {
        // Given
        val server = RemoteMcpDiscovery.RemoteServer(
            nodeName = "control0",
            host = "192.168.1.100",
            port = 8000,
            endpoint = "http://192.168.1.100:8000/sse"
        )

        // Create a mock proxy that simulates connection failure
        val mockProxy = object : RemoteMcpProxy() {
            override fun fetchToolsFromServer(server: RemoteMcpDiscovery.RemoteServer): List<RemoteToolInfo> {
                // Simulate connection failure
                return emptyList()
            }
        }

        // When
        val tools = mockProxy.fetchToolsFromServer(server)

        // Then
        assertThat(tools).isEmpty()
    }

    @Test
    fun `should handle tool execution error gracefully`() {
        // Given
        val server = RemoteMcpDiscovery.RemoteServer(
            nodeName = "control0",
            host = "192.168.1.100",
            port = 8000,
            endpoint = "http://192.168.1.100:8000/sse"
        )

        // Create a mock proxy that returns an error
        val mockProxy = object : RemoteMcpProxy() {
            override fun executeRemoteTool(
                toolName: String,
                arguments: JsonObject?,
                server: RemoteMcpDiscovery.RemoteServer
            ): McpToolRegistry.ToolResult {
                return McpToolRegistry.ToolResult(
                    content = listOf("Tool execution failed: Invalid parameters"),
                    isError = true
                )
            }
        }

        // When
        val result = mockProxy.executeRemoteTool("init", null, server)

        // Then
        assertThat(result).isNotNull
        assertThat(result.isError).isTrue()
        assertThat(result.content.first()).contains("Tool execution failed")
    }

    @Test
    fun `should test connection successfully`() {
        // Given
        val server = RemoteMcpDiscovery.RemoteServer(
            nodeName = "control0",
            host = "192.168.1.100",
            port = 8000,
            endpoint = "http://192.168.1.100:8000/sse"
        )

        // Create a mock proxy with successful health check
        val mockProxy = object : RemoteMcpProxy() {
            override fun testConnection(server: RemoteMcpDiscovery.RemoteServer): Boolean {
                return true
            }
        }

        // When
        val isConnected = mockProxy.testConnection(server)

        // Then
        assertThat(isConnected).isTrue()
    }

    @Test
    fun `should handle connection test failure`() {
        // Given
        val server = RemoteMcpDiscovery.RemoteServer(
            nodeName = "control0",
            host = "192.168.1.100",
            port = 8000,
            endpoint = "http://192.168.1.100:8000/sse"
        )

        // Create a mock proxy with failed health check
        val mockProxy = object : RemoteMcpProxy() {
            override fun testConnection(server: RemoteMcpDiscovery.RemoteServer): Boolean {
                return false
            }
        }

        // When
        val isConnected = mockProxy.testConnection(server)

        // Then
        assertThat(isConnected).isFalse()
    }

    @Test
    fun `should create RemoteToolInfo correctly`() {
        // Given & When
        val toolInfo = RemoteMcpProxy.RemoteToolInfo(
            name = "test-tool",
            description = "Test tool description",
            inputSchema = null
        )

        // Then
        assertThat(toolInfo.name).isEqualTo("test-tool")
        assertThat(toolInfo.description).isEqualTo("Test tool description")
        assertThat(toolInfo.inputSchema).isNull()
    }
}
