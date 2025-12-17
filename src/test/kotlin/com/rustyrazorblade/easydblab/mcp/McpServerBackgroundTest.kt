package com.rustyrazorblade.easydblab.mcp

import com.rustyrazorblade.easydblab.BaseKoinTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class McpServerBackgroundTest : BaseKoinTest() {
    private lateinit var registry: McpToolRegistry

    @BeforeEach
    fun setup() {
        registry = McpToolRegistry()
    }

    @Test
    fun `should handle multiple tool executions`() {
        // Execute status tool multiple times (it's in the MCP command list)
        val result1 = registry.executeTool("status", null)
        val result2 = registry.executeTool("status", null)

        // Both executions should complete without crashing
        assertThat(result1.content).isNotEmpty()
        assertThat(result2.content).isNotEmpty()

        // Results should be consistent
        assertThat(result1.content).isEqualTo(result2.content)
    }
}
