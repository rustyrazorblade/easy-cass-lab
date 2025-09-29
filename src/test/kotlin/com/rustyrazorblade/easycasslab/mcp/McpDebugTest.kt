package com.rustyrazorblade.easycasslab.mcp

import com.rustyrazorblade.easycasslab.BaseKoinTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class McpDebugTest : BaseKoinTest() {
    private lateinit var registry: McpToolRegistry

    @BeforeEach
    fun setup() {
        registry = McpToolRegistry(context)
    }

    @Test
    fun `should list available tools`() {
        // Get tools from registry
        val tools = registry.getTools()

        // Verify we have tools registered
        assertThat(tools).isNotEmpty()

        // Verify some expected tools are present
        val toolNames = tools.map { it.name }
        assertThat(toolNames).contains("init", "up", "down")
    }

    @Test
    fun `should execute tool with debug output`() {
        // Execute a simple tool - clean doesn't require infrastructure
        val result = registry.executeTool("clean", null)

        // Verify execution succeeded
        assertThat(result.isError).isFalse
        assertThat(result.content).isNotEmpty()
    }

    @Test
    fun `should provide detailed error messages in debug mode`() {
        // Try to execute a non-existent tool
        val result = registry.executeTool("non-existent-tool", null)

        // Verify we get an error with details
        assertThat(result.isError).isTrue
        assertThat(result.content.joinToString()).contains("Tool not found")
    }
}
