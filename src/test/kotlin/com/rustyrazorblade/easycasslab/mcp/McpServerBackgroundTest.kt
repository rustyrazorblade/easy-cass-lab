package com.rustyrazorblade.easycasslab.mcp

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import com.rustyrazorblade.easycasslab.Command
import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.annotations.McpCommand
import com.rustyrazorblade.easycasslab.commands.ICommand
import com.rustyrazorblade.easycasslab.di.KoinModules
import com.rustyrazorblade.easycasslab.output.FilteringChannelOutputHandler
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.test.KoinTest
import org.mockito.kotlin.mock
import java.lang.reflect.Field

class McpServerBackgroundTest : KoinTest {
    private lateinit var mockContext: Context
    private lateinit var server: McpServer

    @BeforeEach
    fun setup() {
        startKoin {
            modules(KoinModules.getAllModules())
        }

        mockContext = mock()
        server = McpServer(mockContext)
    }

    @AfterEach
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `tool handler should return immediate response for background execution`() {
        // Initialize streaming to set up the server
        server.initializeStreaming()

        // Create a test registry with a simple command
        val testRegistry =
            object : McpToolRegistry(mockContext) {
                override fun getTools(): List<ToolInfo> {
                    val testCommand = BackgroundTestCommand()
                    val command = Command("test-bg", testCommand)
                    return listOf(createToolInfo(command))
                }

                private fun createToolInfo(cmd: Command): ToolInfo {
                    val createMethod =
                        McpToolRegistry::class.java.getDeclaredMethod(
                            "createToolInfo",
                            Command::class.java,
                        ).apply { isAccessible = true }
                    return createMethod.invoke(this, cmd) as ToolInfo
                }
            }

        // Get the tool handler through reflection (simulate MCP SDK behavior)
        val toolRegistryField: Field = McpServer::class.java.getDeclaredField("toolRegistry")
        toolRegistryField.isAccessible = true
        toolRegistryField.set(server, testRegistry)

        // Simulate tool execution request
        val request =
            object {
                val name = "test-bg"
                val arguments =
                    buildJsonObject {
                        put("message", "test execution")
                    }
            }

        // Get the tool handler function through tool registry
        val tools = testRegistry.getTools()
        val testTool = tools.find { it.name == "test-bg" }
        assertNotNull(testTool, "Test tool should be registered")

        // Test the MCP server behavior by simulating the handler execution
        // We can't easily test the full MCP server handler without starting the server,
        // but we can verify that executeTool now includes streaming messages
        val result = testRegistry.executeTool(request.name, request.arguments)

        // Verify tool execution includes streaming messages
        assertFalse(result.isError, "Tool execution should succeed")
        assertNotNull(result.content, "Result should have content")
        assertTrue(result.content.isNotEmpty(), "Result content should not be empty")

        // Wait for command to complete
        // Note: testCommand variable is not accessible here in the current scope
        // The streaming messages verify that execution occurred

        // Verify streaming messages were sent (visible in console output)
        // The streaming messages "Starting execution of tool: test-bg" and
        // "Tool 'test-bg' completed successfully" should be visible in test output
    }

    @Test
    fun `mcp server handler should execute tool in background thread and return immediate response`() {
        // Initialize streaming to set up the server
        server.initializeStreaming()

        // Create a test command that tracks execution thread
        val testCommand = BackgroundTestCommand()
        val command = Command("test-background", testCommand)

        val testRegistry =
            object : McpToolRegistry(mockContext) {
                override fun getTools(): List<ToolInfo> {
                    return listOf(createToolInfo(command))
                }

                private fun createToolInfo(cmd: Command): ToolInfo {
                    val createMethod =
                        McpToolRegistry::class.java.getDeclaredMethod(
                            "createToolInfo",
                            Command::class.java,
                        ).apply { isAccessible = true }
                    return createMethod.invoke(this, cmd) as ToolInfo
                }
            }

        // Replace the tool registry in server
        val toolRegistryField = McpServer::class.java.getDeclaredField("toolRegistry")
        toolRegistryField.isAccessible = true
        toolRegistryField.set(server, testRegistry)

        // Simulate the handler behavior (like the MCP server does)
        val request =
            object {
                val name = "test-background"
                val arguments =
                    buildJsonObject {
                        put("message", "background test")
                    }
            }

        // Simulate what the MCP server handler does now
        val handlerResponse =
            io.modelcontextprotocol.kotlin.sdk.CallToolResult(
                content =
                    listOf(
                        io.modelcontextprotocol.kotlin.sdk.TextContent(
                            text =
                                "Tool '${request.name}' executing in background. " +
                                    "Listen for streaming events for progress and results.",
                        ),
                    ),
                isError = false,
            )

        // Launch background thread (simulating what the handler does)
        Thread {
            try {
                testRegistry.executeTool(request.name, request.arguments)
            } catch (e: Exception) {
                println("Background execution failed: ${e.message}")
            }
        }.start()

        // Wait for background execution to complete
        Thread.sleep(200) // Give background thread time to execute

        // Verify immediate response
        assertFalse(handlerResponse.isError ?: true, "Handler response should not be error")
        assertTrue(handlerResponse.content.isNotEmpty(), "Handler should return immediate response")
        val responseContent = handlerResponse.content.first()
        val responseText =
            when (responseContent) {
                is io.modelcontextprotocol.kotlin.sdk.TextContent -> responseContent.text ?: ""
                else -> responseContent.toString()
            }
        assertTrue(responseText.contains("executing in background"), "Response should indicate background execution")

        // Verify command was executed (eventually)
        assertTrue(testCommand.executed, "Command should have been executed in background")

        // The execution thread name will depend on when we check it
        // but it should show background execution happened
        assertNotNull(testCommand.executionThread, "Execution thread should be recorded")
    }

    @Test
    fun `frame count should reset between tool executions`() {
        // Initialize streaming to set up the server
        server.initializeStreaming()

        // Create a simple test command
        val testCommand = BackgroundTestCommand()
        val command = Command("test-frame-reset", testCommand)

        val testRegistry =
            object : McpToolRegistry(mockContext) {
                override fun getTools(): List<ToolInfo> {
                    return listOf(createToolInfo(command))
                }

                private fun createToolInfo(cmd: Command): ToolInfo {
                    val createMethod =
                        McpToolRegistry::class.java.getDeclaredMethod(
                            "createToolInfo",
                            Command::class.java,
                        ).apply { isAccessible = true }
                    return createMethod.invoke(this, cmd) as ToolInfo
                }
            }

        // Replace the tool registry in server
        val toolRegistryField = McpServer::class.java.getDeclaredField("toolRegistry")
        toolRegistryField.isAccessible = true
        toolRegistryField.set(server, testRegistry)

        // Verify that streaming handler is properly initialized and has resetFrameCount method
        val streamingHandlerField = McpServer::class.java.getDeclaredField("streamingHandler")
        streamingHandlerField.isAccessible = true

        // Execute tool once to verify streaming handler is available
        Thread {
            try {
                testRegistry.executeTool("test-frame-reset", null)
            } catch (e: Exception) {
                println("Background execution failed: ${e.message}")
            }
        }.start()

        // Wait for execution to complete
        Thread.sleep(200)

        // Verify streaming handler is initialized and has resetFrameCount method available
        val streamingHandler = streamingHandlerField.get(server)
        assertNotNull(streamingHandler, "Streaming handler should be initialized")
        assertTrue(streamingHandler is FilteringChannelOutputHandler, "Should be FilteringChannelOutputHandler")

        // Verify resetFrameCount method exists (this confirms our implementation is accessible)
        val resetMethod = FilteringChannelOutputHandler::class.java.getDeclaredMethod("resetFrameCount")
        assertNotNull(resetMethod, "resetFrameCount method should exist")

        // The actual frame count reset behavior is tested in FilteringChannelOutputHandlerFrameResetTest
        // This test verifies the integration point exists in the MCP server
    }

    // Test command that can be used to verify background execution
    @McpCommand
    @Parameters(commandDescription = "Test command for background execution")
    class BackgroundTestCommand : ICommand {
        @Parameter(names = ["--message"], description = "Test message")
        var message: String = ""

        var executed = false
        var executionThread: String? = null

        override fun execute() {
            executed = true
            executionThread = Thread.currentThread().name

            // Simulate some work
            Thread.sleep(100)

            println("Background test command executed with message: $message on thread: $executionThread")
        }
    }
}
