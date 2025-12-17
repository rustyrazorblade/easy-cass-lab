package com.rustyrazorblade.easydblab.mcp

import com.rustyrazorblade.easydblab.BaseKoinTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class McpDebugTest : BaseKoinTest() {
    private lateinit var registry: McpToolRegistry

    @BeforeEach
    fun setup() {
        registry = McpToolRegistry()
    }

    @Test
    fun `should list available tools`() {
        // Get tools from registry
        val tools = registry.getTools()

        // Verify we have tools registered
        assertThat(tools).isNotEmpty()

        // Verify some expected tools are present (namespaced names)
        val toolNames = tools.map { it.name }
        assertThat(toolNames).contains("status", "cassandra_start", "cassandra_stress_start")
    }

    @Test
    fun `should execute tool with debug output`() {
        // Execute status tool - it doesn't require infrastructure and should work
        val result = registry.executeTool("status", null)

        // Verify execution succeeded (may have error due to no cluster state, but shouldn't crash)
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
