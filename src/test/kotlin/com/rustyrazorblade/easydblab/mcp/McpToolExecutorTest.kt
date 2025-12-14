package com.rustyrazorblade.easydblab.mcp

import com.rustyrazorblade.easydblab.BaseKoinTest
import com.rustyrazorblade.easydblab.commands.PicoCommand
import com.rustyrazorblade.easydblab.output.SubscribableOutputHandler
import io.modelcontextprotocol.server.McpSyncServerExchange
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.koin.core.module.Module
import org.koin.dsl.module
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Tests for McpToolExecutor which manages tool execution on a single-thread executor.
 */
class McpToolExecutorTest : BaseKoinTest() {
    private lateinit var mockExchange: McpSyncServerExchange
    private lateinit var executor: McpToolExecutor

    @BeforeEach
    fun setup() {
        mockExchange = mock()
    }

    override fun additionalTestModules(): List<Module> =
        listOf(
            module {
                // Provide SubscribableOutputHandler for tests
                single<SubscribableOutputHandler> {
                    SubscribableOutputHandler(get())
                }
            },
        )

    @AfterEach
    fun teardown() {
        if (::executor.isInitialized) {
            executor.shutdown()
        }
    }

    @Nested
    inner class SuccessfulExecution {
        @Test
        fun `should execute command and return success result`() {
            executor = McpToolExecutor()
            val wasExecuted = AtomicBoolean(false)

            val entry =
                createTestEntry("test-command") {
                    wasExecuted.set(true)
                }

            val result = executor.execute(entry, null, null, mockExchange) { _, _ -> }

            assertThat(result.isError).isFalse()
            assertThat(wasExecuted.get()).isTrue()
        }

        @Test
        fun `should include tool name in result content`() {
            executor = McpToolExecutor()

            val entry = createTestEntry("my-tool") { }

            val result = executor.execute(entry, null, null, mockExchange) { _, _ -> }

            assertThat(result.isError).isFalse()
            val content = result.content().first()
            assertThat(content.toString()).contains("my-tool")
        }

        @Test
        fun `should call argument mapper with arguments`() {
            executor = McpToolExecutor()
            val receivedArgs = mutableMapOf<String, Any>()

            val entry = createTestEntry("test") { }
            val args = mapOf("key" to "value")

            executor.execute(entry, args, null, mockExchange) { _, mappedArgs ->
                receivedArgs.putAll(mappedArgs)
            }

            assertThat(receivedArgs).containsEntry("key", "value")
        }

        @Test
        fun `should send progress notifications during execution`() {
            executor = McpToolExecutor()

            val entry = createTestEntry("test") { }

            // Pass a progressToken to enable progress notifications
            executor.execute(entry, null, "test-token", mockExchange) { _, _ -> }

            // Verify progress notifications were sent (Starting + Completed)
            verify(mockExchange, atLeast(2)).progressNotification(any())
        }
    }

    @Nested
    inner class ErrorHandling {
        @Test
        fun `should return error result when command throws exception`() {
            executor = McpToolExecutor()

            val entry =
                createTestEntry("failing-command") {
                    throw RuntimeException("Test error")
                }

            val result = executor.execute(entry, null, null, mockExchange) { _, _ -> }

            assertThat(result.isError).isTrue()
            val content = result.content().first()
            assertThat(content.toString()).contains("Test error")
        }
    }

    @Nested
    inner class ConcurrencyControl {
        @Test
        fun `should execute commands sequentially on single thread`() {
            executor = McpToolExecutor()
            val executionOrder = mutableListOf<Int>()
            val completionLatch = CountDownLatch(2)
            val firstCommandStarted = CountDownLatch(1)

            val entry1 =
                createTestEntry("cmd1") {
                    synchronized(executionOrder) { executionOrder.add(1) }
                    firstCommandStarted.countDown() // Signal that first command has started
                    Thread.sleep(100) // Simulate work
                    synchronized(executionOrder) { executionOrder.add(2) }
                    completionLatch.countDown()
                }

            val entry2 =
                createTestEntry("cmd2") {
                    synchronized(executionOrder) { executionOrder.add(3) }
                    completionLatch.countDown()
                }

            // Start first command in background
            Thread {
                executor.execute(entry1, null, null, mockExchange) { _, _ -> }
            }.start()

            // Wait for first command to actually start executing
            firstCommandStarted.await(5, TimeUnit.SECONDS)

            // Now submit second command - it should wait for first to complete
            Thread {
                executor.execute(entry2, null, null, mockExchange) { _, _ -> }
            }.start()

            completionLatch.await(5, TimeUnit.SECONDS)

            // First command should complete (1, 2) before second starts (3)
            assertThat(executionOrder).containsExactly(1, 2, 3)
        }
    }

    @Nested
    inner class Shutdown {
        @Test
        fun `should shutdown cleanly`() {
            executor = McpToolExecutor()

            // Execute a simple command
            val entry = createTestEntry("test") { }
            executor.execute(entry, null, null, mockExchange) { _, _ -> }

            // Shutdown should not throw
            executor.shutdown()
        }
    }

    private fun createTestEntry(
        toolName: String,
        action: () -> Unit,
    ): McpCommandEntry {
        val commandClass = TestCommand::class.java
        return McpCommandEntry(
            toolName = toolName,
            factory = {
                object : PicoCommand {
                    override fun execute() {
                        action()
                    }
                }
            },
            commandClass = commandClass,
        )
    }

    // Helper class for tests
    class TestCommand : PicoCommand {
        override fun execute() {
            // No-op for testing
        }
    }
}
