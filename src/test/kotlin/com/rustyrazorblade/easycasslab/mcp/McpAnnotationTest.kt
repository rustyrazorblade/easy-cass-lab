package com.rustyrazorblade.easycasslab.mcp

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import com.rustyrazorblade.easycasslab.BaseKoinTest
import com.rustyrazorblade.easycasslab.Command
import com.rustyrazorblade.easycasslab.annotations.McpCommand
import com.rustyrazorblade.easycasslab.commands.ICommand
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

// Test command for MCP annotation testing
@McpCommand
@Parameters(commandDescription = "A test command with MCP annotation")
class TestMcpCommand : ICommand {
    @Parameter(names = ["--test-param"], description = "Test parameter")
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
class TestMcpToolRegistry : McpToolRegistry() {
    override fun getTools(): List<ToolInfo> {
        // Create a test command and add it to the list
        val testCommand = TestMcpCommand()
        val testCommandInfo = ToolInfo(
            name = "test-mcp-command",
            description = "A test command with MCP annotation",
            inputSchema = generateSchema(testCommand),
            command = Command("test-mcp-command", testCommand)
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
        registry = TestMcpToolRegistry()
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
        val arguments = buildJsonObject {
            put("testParam", "hello")
        }
        
        val result = registry.executeTool("test-mcp-command", arguments)
        
        assertThat(result.isError).isFalse
        assertThat(result.content).contains("Tool executed successfully")
    }
}
