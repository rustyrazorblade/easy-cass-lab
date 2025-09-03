package com.rustyrazorblade.easycasslab.output

import com.github.dockerjava.api.model.Frame
import com.github.dockerjava.api.model.StreamType
import com.rustyrazorblade.easycasslab.KoinTestHelper
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException

class ChannelOutputHandlerTest {
    private lateinit var outputChannel: Channel<OutputEvent>
    private lateinit var channelHandler: ChannelOutputHandler

    @BeforeEach
    fun setup() {
        KoinTestHelper.startKoin()
        outputChannel = Channel(capacity = Channel.UNLIMITED)
        channelHandler = ChannelOutputHandler(outputChannel)
    }

    @AfterEach
    fun teardown() {
        KoinTestHelper.stopKoin()
        outputChannel.close()
    }

    @Test
    fun `handleFrame should send FrameEvent to channel`() =
        runTest {
            // Given
            val frame = Frame(StreamType.STDOUT, "Hello World\n".toByteArray())

            // When
            channelHandler.handleFrame(frame)

            // Then
            val event = outputChannel.tryReceive().getOrNull()
            assertInstanceOf(OutputEvent.FrameEvent::class.java, event)
            val frameEvent = event as OutputEvent.FrameEvent
            assertEquals(frame, frameEvent.frame)
        }

    @Test
    fun `handleMessage should send MessageEvent to channel`() =
        runTest {
            // Given
            val message = "Test message"

            // When
            channelHandler.handleMessage(message)

            // Then
            val event = outputChannel.tryReceive().getOrNull()
            assertInstanceOf(OutputEvent.MessageEvent::class.java, event)
            val messageEvent = event as OutputEvent.MessageEvent
            assertEquals(message, messageEvent.message)
        }

    @Test
    fun `handleError should send ErrorEvent to channel with message only`() =
        runTest {
            // Given
            val errorMessage = "Test error"

            // When
            channelHandler.handleError(errorMessage)

            // Then
            val event = outputChannel.tryReceive().getOrNull()
            assertInstanceOf(OutputEvent.ErrorEvent::class.java, event)
            val errorEvent = event as OutputEvent.ErrorEvent
            assertEquals(errorMessage, errorEvent.message)
            assertNull(errorEvent.throwable)
        }

    @Test
    fun `handleError should send ErrorEvent to channel with message and throwable`() =
        runTest {
            // Given
            val errorMessage = "Test error with exception"
            val throwable = IOException("Test IOException")

            // When
            channelHandler.handleError(errorMessage, throwable)

            // Then
            val event = outputChannel.tryReceive().getOrNull()
            assertInstanceOf(OutputEvent.ErrorEvent::class.java, event)
            val errorEvent = event as OutputEvent.ErrorEvent
            assertEquals(errorMessage, errorEvent.message)
            assertEquals(throwable, errorEvent.throwable)
        }

    @Test
    fun `close should send CloseEvent to channel`() =
        runTest {
            // When
            channelHandler.close()

            // Then
            val event = outputChannel.tryReceive().getOrNull()
            assertInstanceOf(OutputEvent.CloseEvent::class.java, event)
        }

    @Test
    fun `multiple events should be sent in correct order`() =
        runTest {
            // Given
            val frame = Frame(StreamType.STDERR, "Error output".toByteArray())
            val message = "Status message"
            val errorMessage = "Error message"

            // When
            channelHandler.handleFrame(frame)
            channelHandler.handleMessage(message)
            channelHandler.handleError(errorMessage)
            channelHandler.close()

            // Then
            val events = mutableListOf<OutputEvent>()
            repeat(4) {
                val event = outputChannel.tryReceive().getOrNull()
                if (event != null) {
                    events.add(event)
                }
            }

            assertEquals(4, events.size)

            // Verify event types and order
            assertInstanceOf(OutputEvent.FrameEvent::class.java, events[0])
            assertInstanceOf(OutputEvent.MessageEvent::class.java, events[1])
            assertInstanceOf(OutputEvent.ErrorEvent::class.java, events[2])
            assertInstanceOf(OutputEvent.CloseEvent::class.java, events[3])

            // Verify content
            assertEquals(frame, (events[0] as OutputEvent.FrameEvent).frame)
            assertEquals(message, (events[1] as OutputEvent.MessageEvent).message)
            assertEquals(errorMessage, (events[2] as OutputEvent.ErrorEvent).message)
        }

    @Test
    fun `handler should work with closed channel gracefully`() =
        runBlocking {
            // Given - close the channel first
            outputChannel.close()

            // When - operations should not throw exceptions
            channelHandler.handleMessage("This should not crash")
            channelHandler.handleError("This should not crash either")
            channelHandler.close()

            // Then - no exceptions should be thrown (test passes if no exception)
            // The warning logs are expected behavior
        }

    @Test
    fun `OutputEvent sealed classes should have correct properties`() {
        // Given
        val frame = Frame(StreamType.STDOUT, "test".toByteArray())
        val message = "test message"
        val errorMessage = "test error"
        val throwable = RuntimeException("test exception")

        // When
        val frameEvent = OutputEvent.FrameEvent(frame)
        val messageEvent = OutputEvent.MessageEvent(message)
        val errorEvent = OutputEvent.ErrorEvent(errorMessage, throwable)
        val closeEvent = OutputEvent.CloseEvent

        // Then
        assertEquals(frame, frameEvent.frame)
        assertEquals(message, messageEvent.message)
        assertEquals(errorMessage, errorEvent.message)
        assertEquals(throwable, errorEvent.throwable)

        // CloseEvent is a singleton object
        assertEquals(OutputEvent.CloseEvent, closeEvent)
    }

    @Test
    fun `ErrorEvent can be created without throwable`() {
        // Given
        val errorMessage = "test error"

        // When
        val errorEvent = OutputEvent.ErrorEvent(errorMessage)

        // Then
        assertEquals(errorMessage, errorEvent.message)
        assertNull(errorEvent.throwable)
    }
}
