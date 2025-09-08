package com.rustyrazorblade.easycasslab.mcp

import com.rustyrazorblade.easycasslab.BaseKoinTest
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
        // Execute clean tool multiple times
        val result1 = registry.executeTool("clean", null)
        val result2 = registry.executeTool("clean", null)
        
        // Both executions should succeed
        assertThat(result1.isError).isFalse
        assertThat(result2.isError).isFalse
        
        // Results should be consistent
        assertThat(result1.content).isEqualTo(result2.content)
    }
}
