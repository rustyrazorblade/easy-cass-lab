package com.rustyrazorblade.easycasslab.mcp

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import com.rustyrazorblade.easycasslab.Command
import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.commands.ICommand
import com.rustyrazorblade.easycasslab.di.KoinModules
import com.rustyrazorblade.easycasslab.output.CompositeOutputHandler
import com.rustyrazorblade.easycasslab.output.FilteringChannelOutputHandler
import com.rustyrazorblade.easycasslab.output.OutputEvent
import com.rustyrazorblade.easycasslab.output.OutputHandler
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.test.KoinTest
import org.koin.test.inject
import org.mockito.kotlin.mock

class McpToolRegistryStreamingTest : KoinTest {
    private lateinit var context: Context
    private lateinit var registry: McpToolRegistry
    private lateinit var streamingChannel: Channel<OutputEvent>
    private lateinit var streamingHandler: FilteringChannelOutputHandler

    @BeforeEach
    fun setup() {
        // Initialize Koin for dependency injection
        startKoin {
            modules(KoinModules.getAllModules())
        }

        context = mock()
        registry = McpToolRegistry(context)

        // Set up streaming channel and handler
        streamingChannel = Channel(Channel.UNLIMITED)
        streamingHandler = FilteringChannelOutputHandler(streamingChannel)
    }

    @AfterEach
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `tool registry should work with CompositeOutputHandler`() {
        val outputHandler: OutputHandler by inject()

        // Verify that the default output handler is CompositeOutputHandler
        assertTrue(outputHandler is CompositeOutputHandler, "Should use CompositeOutputHandler")

        // The registry should be able to use the injected handler
        assertNotNull(registry, "Registry should be creatable with CompositeOutputHandler")
    }

    @Test
    fun `tool execution should work with streaming-enabled CompositeOutputHandler`() =
        runBlocking {
            val outputHandler: OutputHandler by inject()
            val compositeHandler = outputHandler as CompositeOutputHandler

            // Add streaming handler to composite
            compositeHandler.addHandler(streamingHandler)

            // Create a test command that generates output
            val testCommand = StreamingTestCommand()
            val command = Command("test-streaming", testCommand)

            // Create a test registry with our command
            val testRegistry =
                object : McpToolRegistry(context) {
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

            // Execute the tool
            val arguments =
                buildJsonObject {
                    put("message", "streaming test")
                }

            val result = testRegistry.executeTool("test-streaming", arguments)

            // Verify execution succeeded
            assertFalse(result.isError, "Tool execution should succeed")
            assertEquals(1, testCommand.executionCount, "Command should have been executed once")

            // Note: The actual output capture would require integration with the command execution framework
            // This test verifies that streaming doesn't break tool execution
        }

    @Test
    fun `multiple handlers in CompositeOutputHandler should not interfere with tool execution`() =
        runBlocking {
            val outputHandler: OutputHandler by inject()
            val compositeHandler = outputHandler as CompositeOutputHandler

            // Add multiple handlers
            val channel1 = Channel<OutputEvent>(Channel.UNLIMITED)
            val channel2 = Channel<OutputEvent>(Channel.UNLIMITED)
            compositeHandler.addHandler(FilteringChannelOutputHandler(channel1))
            compositeHandler.addHandler(FilteringChannelOutputHandler(channel2))

            // Create and execute a test command
            val testCommand = SimpleStreamingCommand()
            val command = Command("test-multi", testCommand)

            val testRegistry =
                object : McpToolRegistry(context) {
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

            val result = testRegistry.executeTool("test-multi", null)

            // Verify execution succeeded despite multiple handlers
            assertFalse(result.isError, "Tool execution should succeed with multiple handlers")
            assertEquals(1, testCommand.executionCount, "Command should have been executed once")
        }

    @Test
    fun `tool registry should handle streaming handler addition gracefully`() {
        val outputHandler: OutputHandler by inject()
        val compositeHandler = outputHandler as CompositeOutputHandler

        val initialHandlerCount = compositeHandler.getHandlerCount()

        // Add streaming handler
        compositeHandler.addHandler(streamingHandler)

        assertEquals(
            initialHandlerCount + 1,
            compositeHandler.getHandlerCount(),
            "Should have one additional handler",
        )

        // Registry should still be functional
        assertNotNull(registry, "Registry should remain functional after handler modification")
    }

    // Test command classes
    @Parameters(commandDescription = "Test command for streaming")
    class StreamingTestCommand : ICommand {
        @Parameter(names = ["--message"], description = "Test message")
        var message: String = ""

        var executionCount = 0

        override fun execute() {
            executionCount++
            // Simulate some output that would be captured by output handlers
            println("Executing streaming test command with message: $message")
        }
    }

    @Parameters(commandDescription = "Simple test command")
    class SimpleStreamingCommand : ICommand {
        var executionCount = 0

        override fun execute() {
            executionCount++
            println("Simple streaming command executed")
        }
    }
}
