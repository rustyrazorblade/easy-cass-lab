package com.rustyrazorblade.easycasslab.docker

import com.github.dockerjava.api.model.Frame
import com.github.dockerjava.api.model.StreamType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream

class OutputHandlerTest {
    @Test
    fun `BufferedOutputHandler captures stdout correctly`() {
        val handler = BufferedOutputHandler()
        val frame = Frame(StreamType.STDOUT, "Hello World\n".toByteArray())

        handler.handleFrame(frame)

        assertEquals("Hello World\n", handler.stdout)
        assertEquals("", handler.stderr)
    }

    @Test
    fun `BufferedOutputHandler captures stderr correctly`() {
        val handler = BufferedOutputHandler()
        val frame = Frame(StreamType.STDERR, "Error occurred\n".toByteArray())

        handler.handleFrame(frame)

        assertEquals("", handler.stdout)
        assertEquals("Error occurred\n", handler.stderr)
    }

    @Test
    fun `BufferedOutputHandler captures messages correctly`() {
        val handler = BufferedOutputHandler()

        handler.handleMessage("Status update 1")
        handler.handleMessage("Status update 2")

        assertEquals(listOf("Status update 1", "Status update 2"), handler.messages)
    }

    @Test
    fun `BufferedOutputHandler captures errors correctly`() {
        val handler = BufferedOutputHandler()
        val exception = RuntimeException("Test error")

        handler.handleError("Error occurred", exception)
        handler.handleError("Another error", null)

        assertEquals(2, handler.errors.size)
        assertEquals("Error occurred" to exception, handler.errors[0])
        assertEquals("Another error" to null, handler.errors[1])
    }

    @Test
    fun `BufferedOutputHandler clear works correctly`() {
        val handler = BufferedOutputHandler()
        handler.handleFrame(Frame(StreamType.STDOUT, "data".toByteArray()))
        handler.handleMessage("message")
        handler.handleError("error", null)

        handler.clear()

        assertEquals("", handler.stdout)
        assertEquals("", handler.stderr)
        assertTrue(handler.messages.isEmpty())
        assertTrue(handler.errors.isEmpty())
    }

    @Test
    fun `ConsoleOutputHandler writes to stdout and stderr`() {
        val handler = ConsoleOutputHandler()

        // Capture stdout and stderr
        val originalOut = System.out
        val originalErr = System.err
        val capturedOut = ByteArrayOutputStream()
        val capturedErr = ByteArrayOutputStream()

        try {
            System.setOut(PrintStream(capturedOut))
            System.setErr(PrintStream(capturedErr))

            handler.handleFrame(Frame(StreamType.STDOUT, "stdout text".toByteArray()))
            handler.handleFrame(Frame(StreamType.STDERR, "stderr text".toByteArray()))
            handler.handleMessage("message text")
            handler.handleError("error text", null)

            // Flush the streams to ensure all data is written
            System.out.flush()
            System.err.flush()

            val outContent = capturedOut.toString()
            val errContent = capturedErr.toString()

            assertEquals("stdout textmessage text\n", outContent)
            assertEquals("stderr texterror text\n", errContent)
        } finally {
            System.setOut(originalOut)
            System.setErr(originalErr)
        }
    }

    @Test
    fun `CompositeOutputHandler delegates to all handlers`() {
        val handler1 = BufferedOutputHandler()
        val handler2 = BufferedOutputHandler()
        val composite = CompositeOutputHandler(handler1, handler2)

        val frame = Frame(StreamType.STDOUT, "test data".toByteArray())
        composite.handleFrame(frame)
        composite.handleMessage("test message")
        composite.handleError("test error", null)

        // Both handlers should receive the same data
        assertEquals("test data", handler1.stdout)
        assertEquals("test data", handler2.stdout)
        assertEquals(listOf("test message"), handler1.messages)
        assertEquals(listOf("test message"), handler2.messages)
        assertEquals(1, handler1.errors.size)
        assertEquals(1, handler2.errors.size)
    }

    @Test
    fun `LoggerOutputHandler processes frames correctly`() {
        val handler = LoggerOutputHandler("TestLogger")

        // Just ensure it doesn't throw exceptions
        handler.handleFrame(Frame(StreamType.STDOUT, "log line\n".toByteArray()))
        handler.handleFrame(Frame(StreamType.STDERR, "error line\n".toByteArray()))
        handler.handleMessage("status message")
        handler.handleError("error message", RuntimeException("test"))
        handler.close()
    }
}
