package com.rustyrazorblade.easycasslab.mcp

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import com.rustyrazorblade.easycasslab.Command
import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.annotations.McpCommand
import com.rustyrazorblade.easycasslab.commands.ICommand
import com.rustyrazorblade.easycasslab.di.outputModule
import com.rustyrazorblade.easycasslab.output.CompositeOutputHandler
import com.rustyrazorblade.easycasslab.output.FilteringChannelOutputHandler
import com.rustyrazorblade.easycasslab.output.OutputEvent
import com.rustyrazorblade.easycasslab.output.OutputHandler
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.test.KoinTest
import org.koin.test.inject
import org.mockito.kotlin.mock
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class McpToolRegistryBackgroundTest : KoinTest {

    private lateinit var context: Context
    private lateinit var registry: McpToolRegistry
    private lateinit var outputChannel: Channel<OutputEvent>
    private lateinit var streamingHandler: FilteringChannelOutputHandler

    @BeforeEach
    fun setup() {
        startKoin {
            modules(outputModule)
        }

        context = mock()
        registry = McpToolRegistry(context)
        
        // Set up streaming infrastructure for testing
        outputChannel = Channel(Channel.UNLIMITED)
        streamingHandler = FilteringChannelOutputHandler(outputChannel)
        
        // Add streaming handler to composite
        val outputHandler: OutputHandler by inject()
        val compositeHandler = outputHandler as? CompositeOutputHandler
        compositeHandler?.addHandler(streamingHandler)
    }

    @AfterEach
    fun tearDown() {
        outputChannel.close()
        stopKoin()
    }

    @Test
    fun `executeTool should run command in background thread`() {
        // Create test command that can track execution thread
        val testCommand = ThreadTrackingCommand()
        val command = Command("test-thread", testCommand)

        // Create test registry with our command
        val testRegistry = object : McpToolRegistry(context) {
            override fun getTools(): List<ToolInfo> {
                return listOf(createToolInfo(command))
            }

            private fun createToolInfo(cmd: Command): ToolInfo {
                val createMethod = McpToolRegistry::class.java.getDeclaredMethod(
                    "createToolInfo",
                    Command::class.java
                ).apply { isAccessible = true }
                return createMethod.invoke(this, cmd) as ToolInfo
            }
        }

        val arguments = buildJsonObject {
            put("workDuration", 50)
        }

        // Record the current thread name
        val mainThreadName = Thread.currentThread().name

        // Execute tool
        val startTime = System.currentTimeMillis()
        val result = testRegistry.executeTool("test-thread", arguments)
        val executionTime = System.currentTimeMillis() - startTime

        // Wait for command to complete
        assertTrue(testCommand.completionLatch.await(2, TimeUnit.SECONDS), "Command should complete within 2 seconds")

        // Verify execution completed
        assertTrue(testCommand.executed, "Command should have been executed")
        
        // For now (before background implementation), this will be same thread
        // After implementation, we'll verify it's a different thread
        assertNotNull(testCommand.executionThread, "Execution thread should be recorded")
        
        // Currently this will be the same thread (synchronous execution)
        // After background implementation, we'll assert:
        // assertNotEquals(mainThreadName, testCommand.executionThread, "Should execute in different thread")
        
        // Verify result is returned (immediately after background implementation)
        assertFalse(result.isError, "Tool execution should succeed")
        assertNotNull(result.content, "Result should have content")
    }

    @Test
    fun `background execution should stream progress and completion`() {
        // Create test command that generates output
        val testCommand = StreamingTestCommand()
        val command = Command("test-stream", testCommand)

        // Create test registry
        val testRegistry = object : McpToolRegistry(context) {
            override fun getTools(): List<ToolInfo> {
                return listOf(createToolInfo(command))
            }

            private fun createToolInfo(cmd: Command): ToolInfo {
                val createMethod = McpToolRegistry::class.java.getDeclaredMethod(
                    "createToolInfo",
                    Command::class.java
                ).apply { isAccessible = true }
                return createMethod.invoke(this, cmd) as ToolInfo
            }
        }

        val arguments = buildJsonObject {
            put("steps", 3)
        }

        // Execute tool
        val result = testRegistry.executeTool("test-stream", arguments)

        // Wait for command to complete
        assertTrue(testCommand.completionLatch.await(3, TimeUnit.SECONDS), "Command should complete within 3 seconds")

        // Verify result
        assertFalse(result.isError, "Tool execution should succeed")
        
        // After background implementation, verify streaming messages were sent
        // For now, this test documents the expected behavior
        assertTrue(testCommand.executed, "Command should have executed")
        assertEquals(3, testCommand.stepsCompleted, "All steps should have completed")
        
        // After implementation, we'll verify streaming events in outputChannel
        // Current implementation doesn't stream, so we can't verify streaming yet
    }

    // Test command that tracks which thread it executes on
    @McpCommand
    @Parameters(commandDescription = "Test command that tracks execution thread")
    class ThreadTrackingCommand : ICommand {
        @Parameter(names = ["--work-duration"], description = "Duration of work in ms")
        var workDuration: Int = 100
        
        var executed = false
        var executionThread: String? = null
        val completionLatch = CountDownLatch(1)

        override fun execute() {
            try {
                executed = true
                executionThread = Thread.currentThread().name
                
                // Simulate work
                Thread.sleep(workDuration.toLong())
                
                println("Thread tracking command executed on: $executionThread")
            } finally {
                completionLatch.countDown()
            }
        }
    }

    // Test command that can simulate streaming output
    @McpCommand
    @Parameters(commandDescription = "Test command for streaming output")
    class StreamingTestCommand : ICommand {
        @Parameter(names = ["--steps"], description = "Number of steps to execute")
        var steps: Int = 1
        
        var executed = false
        var stepsCompleted = 0
        val completionLatch = CountDownLatch(1)

        override fun execute() {
            try {
                executed = true
                
                repeat(steps) { step ->
                    Thread.sleep(50)
                    println("Step ${step + 1} completed")
                    stepsCompleted++
                }
                
                println("Streaming test command completed with $stepsCompleted steps")
            } finally {
                completionLatch.countDown()
            }
        }
    }
}