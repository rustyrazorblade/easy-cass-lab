package com.rustyrazorblade.easydblab.mcp

import com.github.dockerjava.api.model.Frame
import com.github.dockerjava.api.model.StreamType
import io.modelcontextprotocol.server.McpSyncServerExchange
import io.modelcontextprotocol.spec.McpSchema.ProgressNotification
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

/**
 * Tests for McpProgressNotifier which sends output handler messages
 * as MCP progress notifications to the client.
 *
 * Progress notifications are only sent when a progressToken is provided by the client.
 */
class McpProgressNotifierTest {
    private lateinit var mockExchange: McpSyncServerExchange

    @Nested
    inner class WithProgressToken {
        private lateinit var notifier: McpProgressNotifier

        @BeforeEach
        fun setup() {
            mockExchange = mock()
            notifier = McpProgressNotifier(mockExchange, "test-token")
        }

        @Test
        fun `should send progress notification for each message`() {
            notifier.publishMessage("Test message")

            val captor = argumentCaptor<ProgressNotification>()
            verify(mockExchange).progressNotification(captor.capture())

            val notification = captor.firstValue
            assertThat(notification.message()).isEqualTo("Test message")
        }

        @Test
        fun `should use client-provided progress token`() {
            notifier.publishMessage("Test message")

            val captor = argumentCaptor<ProgressNotification>()
            verify(mockExchange).progressNotification(captor.capture())

            val notification = captor.firstValue
            assertThat(notification.progressToken()).isEqualTo("test-token")
        }

        @Test
        fun `should increment progress count for each message`() {
            notifier.publishMessage("Message 1")
            notifier.publishMessage("Message 2")
            notifier.publishMessage("Message 3")

            val captor = argumentCaptor<ProgressNotification>()
            verify(mockExchange, times(3)).progressNotification(captor.capture())

            val progressValues = captor.allValues.map { it.progress() }
            assertThat(progressValues).containsExactly(1.0, 2.0, 3.0)
        }

        @Test
        fun `should send progress notification with ERROR prefix for errors`() {
            notifier.publishError("Something went wrong", RuntimeException("test"))

            val captor = argumentCaptor<ProgressNotification>()
            verify(mockExchange).progressNotification(captor.capture())

            val notification = captor.firstValue
            assertThat(notification.message()).contains("[ERROR]")
            assertThat(notification.message()).contains("Something went wrong")
        }

        @Test
        fun `should handle error without throwable`() {
            notifier.publishError("Simple error", null)

            val captor = argumentCaptor<ProgressNotification>()
            verify(mockExchange).progressNotification(captor.capture())

            val notification = captor.firstValue
            assertThat(notification.message()).contains("[ERROR]")
        }

        @Test
        fun `should not send notification for frames`() {
            val frame = Frame(StreamType.STDOUT, "test output".toByteArray())

            notifier.publishFrame(frame)

            verify(mockExchange, never()).progressNotification(any())
        }

        @Test
        fun `close should be no-op`() {
            notifier.close()

            verify(mockExchange, never()).progressNotification(any())
        }
    }

    @Nested
    inner class WithoutProgressToken {
        private lateinit var notifier: McpProgressNotifier

        @BeforeEach
        fun setup() {
            mockExchange = mock()
            notifier = McpProgressNotifier(mockExchange, null)
        }

        @Test
        fun `should not send notifications when progressToken is null`() {
            notifier.publishMessage("Test message")
            notifier.publishError("Error", null)

            verify(mockExchange, never()).progressNotification(any())
        }

        @Test
        fun `should still increment internal counter even without token`() {
            // This test ensures the notifier doesn't crash when token is null
            notifier.publishMessage("Message 1")
            notifier.publishMessage("Message 2")
            notifier.publishError("Error", null)

            // No exceptions thrown, no notifications sent
            verify(mockExchange, never()).progressNotification(any())
        }
    }
}
