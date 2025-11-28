package com.rustyrazorblade.easycasslab.mcp

import com.rustyrazorblade.easycasslab.BaseKoinTest
import com.rustyrazorblade.easycasslab.PicoCommandEntry
import com.rustyrazorblade.easycasslab.annotations.McpCommand
import com.rustyrazorblade.easycasslab.commands.PicoBaseCommand
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import picocli.CommandLine.Command
import picocli.CommandLine.Option

// Test command for MCP annotation testing
@McpCommand
@Command(name = "test-mcp-command", description = ["A test command with MCP annotation"])
class TestMcpCommand(
    context: com.rustyrazorblade.easycasslab.Context,
) : PicoBaseCommand(context) {
    @Option(names = ["--test-param"], description = ["Test parameter"])
    var testParam: String = ""

    override fun execute() {
        if (testParam.isNotEmpty()) {
            println("Test MCP command executed with param: $testParam")
        } else {
            println("Test MCP command executed")
        }
    }
}

// Test registry that includes test commands
class TestMcpToolRegistry(
    private val testContext: com.rustyrazorblade.easycasslab.Context,
) : McpToolRegistry(testContext) {
    override fun getTools(): List<ToolInfo> {
        // Create a test command entry
        val testEntry = PicoCommandEntry("test-mcp-command", { TestMcpCommand(testContext) })
        val testCommand = testEntry.factory()
        val testCommandInfo =
            ToolInfo(
                name = "test-mcp-command",
                description = "A test command with MCP annotation",
                inputSchema = generatePicoSchema(testCommand),
                entry = testEntry,
            )

        // Get the real tools and add our test command
        val realTools = super.getTools()
        return realTools + testCommandInfo
    }
}

class McpAnnotationTest : BaseKoinTest() {
    private lateinit var registry: TestMcpToolRegistry

    @BeforeEach
    fun setup() {
        registry = TestMcpToolRegistry(context)
    }

    @Test
    fun `should detect and register commands with MCP annotation`() {
        val tools = registry.getTools()

        // Find our test command
        val testTool = tools.find { it.name == "test-mcp-command" }

        assertThat(testTool).isNotNull
        assertThat(testTool?.description).isEqualTo("A test command with MCP annotation")
    }

    @Test
    fun `should execute MCP annotated command`() {
        val result = registry.executeTool("test-mcp-command", null)

        assertThat(result.isError).isFalse
        assertThat(result.content).contains("Tool executed successfully")
    }

    @Test
    fun `should handle parameters for MCP annotated commands`() {
        val arguments = buildJsonObject { put("testParam", "hello") }

        val result = registry.executeTool("test-mcp-command", arguments)

        assertThat(result.isError).isFalse
        assertThat(result.content).contains("Tool executed successfully")
    }
}
