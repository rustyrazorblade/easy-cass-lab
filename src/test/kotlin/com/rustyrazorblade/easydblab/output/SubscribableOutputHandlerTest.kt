package com.rustyrazorblade.easydblab.output

import com.github.dockerjava.api.model.Frame
import com.github.dockerjava.api.model.StreamType
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

/**
 * Tests for SubscribableOutputHandler which supports dynamic subscription.
 */
class SubscribableOutputHandlerTest {
    private lateinit var defaultHandler: OutputHandler
    private lateinit var handler: SubscribableOutputHandler

    @BeforeEach
    fun setup() {
        defaultHandler = mock()
        handler = SubscribableOutputHandler(defaultHandler)
    }

    @Nested
    inner class DefaultBehavior {
        @Test
        fun `should always send to default handler`() {
            handler.publishMessage("test message")

            verify(defaultHandler).publishMessage("test message")
        }

        @Test
        fun `should send error to default handler`() {
            val exception = RuntimeException("test")
            handler.publishError("error", exception)

            verify(defaultHandler).publishError("error", exception)
        }

        @Test
        fun `should send frame to default handler`() {
            val frame = Frame(StreamType.STDOUT, "test".toByteArray())
            handler.publishFrame(frame)

            verify(defaultHandler).publishFrame(frame)
        }
    }

    @Nested
    inner class Subscription {
        @Test
        fun `should send messages to subscriber after subscribe`() {
            val subscriber: OutputHandler = mock()
            handler.subscribe(subscriber)

            handler.publishMessage("test message")

            verify(subscriber).publishMessage("test message")
        }

        @Test
        fun `should not send messages to subscriber after unsubscribe`() {
            val subscriber: OutputHandler = mock()
            handler.subscribe(subscriber)
            handler.unsubscribe(subscriber)

            handler.publishMessage("test message")

            verify(subscriber, never()).publishMessage("test message")
        }

        @Test
        fun `should send errors to subscriber`() {
            val subscriber: OutputHandler = mock()
            val exception = RuntimeException("test")
            handler.subscribe(subscriber)

            handler.publishError("error", exception)

            verify(subscriber).publishError("error", exception)
        }

        @Test
        fun `should send frames to subscriber`() {
            val subscriber: OutputHandler = mock()
            val frame = Frame(StreamType.STDOUT, "test".toByteArray())
            handler.subscribe(subscriber)

            handler.publishFrame(frame)

            verify(subscriber).publishFrame(frame)
        }

        @Test
        fun `should support multiple subscribers`() {
            val subscriber1: OutputHandler = mock()
            val subscriber2: OutputHandler = mock()
            handler.subscribe(subscriber1)
            handler.subscribe(subscriber2)

            handler.publishMessage("test message")

            verify(subscriber1).publishMessage("test message")
            verify(subscriber2).publishMessage("test message")
        }

        @Test
        fun `should only unsubscribe the specified handler`() {
            val subscriber1: OutputHandler = mock()
            val subscriber2: OutputHandler = mock()
            handler.subscribe(subscriber1)
            handler.subscribe(subscriber2)
            handler.unsubscribe(subscriber1)

            handler.publishMessage("test message")

            verify(subscriber1, never()).publishMessage("test message")
            verify(subscriber2).publishMessage("test message")
        }
    }

    @Nested
    inner class Close {
        @Test
        fun `should close default handler`() {
            handler.close()

            verify(defaultHandler).close()
        }

        @Test
        fun `should close all subscribers`() {
            val subscriber1: OutputHandler = mock()
            val subscriber2: OutputHandler = mock()
            handler.subscribe(subscriber1)
            handler.subscribe(subscriber2)

            handler.close()

            verify(subscriber1).close()
            verify(subscriber2).close()
        }
    }
}
