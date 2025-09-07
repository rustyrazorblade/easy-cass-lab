package com.rustyrazorblade.easycasslab.mcp

import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.di.KoinModules
import com.rustyrazorblade.easycasslab.output.CompositeOutputHandler
import com.rustyrazorblade.easycasslab.output.FilteringChannelOutputHandler
import com.rustyrazorblade.easycasslab.output.OutputHandler
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
import java.lang.reflect.Field
import java.util.concurrent.Semaphore

class McpServerStreamingTest : KoinTest {
    private lateinit var mockContext: Context

    @BeforeEach
    fun setup() {
        mockContext = mock()

        startKoin {
            modules(KoinModules.getAllModules())
        }
    }

    @AfterEach
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `default OutputHandler should be CompositeOutputHandler for streaming integration`() {
        val outputHandler: OutputHandler by inject()
        assertTrue(outputHandler is CompositeOutputHandler, "Default output handler should be CompositeOutputHandler")

        val compositeHandler = outputHandler as CompositeOutputHandler
        assertTrue(compositeHandler.getHandlerCount() >= 2, "Should have at least Logger and Console handlers")

        // This test verifies that the injected output handler is ready for streaming integration
        // The actual streaming handler will be added when initializeStreaming() is implemented
    }

    @Test
    fun `MCP server should maintain semaphore-based single command execution`() {
        val server = McpServer(mockContext)

        // Use reflection to access the private executionSemaphore field for testing
        val semaphoreField: Field = McpServer::class.java.getDeclaredField("executionSemaphore")
        semaphoreField.isAccessible = true
        val semaphore = semaphoreField.get(server) as Semaphore

        // Initially should be available
        assertTrue(semaphore.tryAcquire(), "Semaphore should be available initially")

        // After acquiring, should not be available
        assertFalse(semaphore.tryAcquire(), "Semaphore should not be available after acquisition")

        // After releasing, should be available again
        semaphore.release()
        assertTrue(semaphore.tryAcquire(), "Semaphore should be available after release")

        // Clean up
        semaphore.release()
    }

    @Test
    fun `MCP server should be instantiable with mock context`() {
        // This test verifies that we can create an MCP server instance
        val server = McpServer(mockContext)
        assertNotNull(server, "Should be able to create McpServer instance")
    }

    @Test
    fun `initializeStreaming should add FilteringChannelOutputHandler to CompositeOutputHandler`() {
        val outputHandler: OutputHandler by inject()
        val compositeHandler = outputHandler as CompositeOutputHandler
        val initialHandlerCount = compositeHandler.getHandlerCount()

        val server = McpServer(mockContext)
        server.initializeStreaming()

        // Verify that a FilteringChannelOutputHandler was added
        assertEquals(
            initialHandlerCount + 1,
            compositeHandler.getHandlerCount(),
            "FilteringChannelOutputHandler should be added to composite",
        )

        val handlers = compositeHandler.getHandlers()
        assertTrue(
            handlers.any { it is FilteringChannelOutputHandler },
            "CompositeOutputHandler should contain FilteringChannelOutputHandler",
        )
    }

    @Test
    fun `initializeStreaming should be idempotent`() {
        val outputHandler: OutputHandler by inject()
        val compositeHandler = outputHandler as CompositeOutputHandler

        val server = McpServer(mockContext)

        // Initialize streaming twice
        server.initializeStreaming()
        val handlerCountAfterFirst = compositeHandler.getHandlerCount()

        server.initializeStreaming()
        val handlerCountAfterSecond = compositeHandler.getHandlerCount()

        // Should not add duplicate handlers
        assertEquals(
            handlerCountAfterFirst,
            handlerCountAfterSecond,
            "Should not add duplicate FilteringChannelOutputHandler",
        )
    }
}
