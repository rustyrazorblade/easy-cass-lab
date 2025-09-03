package com.rustyrazorblade.easycasslab.di

import com.rustyrazorblade.easycasslab.output.CompositeOutputHandler
import com.rustyrazorblade.easycasslab.output.ConsoleOutputHandler
import com.rustyrazorblade.easycasslab.output.LoggerOutputHandler
import com.rustyrazorblade.easycasslab.output.OutputHandler
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.test.KoinTest
import org.koin.test.inject

class OutputModuleTest : KoinTest {
    
    @BeforeEach
    fun setup() {
        startKoin {
            modules(outputModule)
        }
    }
    
    @AfterEach
    fun tearDown() {
        stopKoin()
    }
    
    @Test
    fun `default OutputHandler should be CompositeOutputHandler with Logger and Console handlers`() {
        val handler: OutputHandler by inject()
        
        assertTrue(handler is CompositeOutputHandler, "Default OutputHandler should be CompositeOutputHandler")
        
        val composite = handler as CompositeOutputHandler
        assertEquals(2, composite.getHandlerCount(), "CompositeOutputHandler should contain exactly 2 handlers")
        
        val handlers = composite.getHandlers()
        assertTrue(handlers.any { it is LoggerOutputHandler }, "Should contain LoggerOutputHandler")
        assertTrue(handlers.any { it is ConsoleOutputHandler }, "Should contain ConsoleOutputHandler")
    }
    
    @Test
    fun `named console handler should be ConsoleOutputHandler instance`() {
        val consoleHandler: OutputHandler by inject(qualifier = org.koin.core.qualifier.named("console"))
        
        assertTrue(consoleHandler is ConsoleOutputHandler, "Named 'console' handler should be ConsoleOutputHandler")
    }
    
    @Test
    fun `named logger handler should be LoggerOutputHandler instance`() {
        val loggerHandler: OutputHandler by inject(qualifier = org.koin.core.qualifier.named("logger"))
        
        assertTrue(loggerHandler is LoggerOutputHandler, "Named 'logger' handler should be LoggerOutputHandler")
    }
    
    @Test
    fun `default OutputHandler should be singleton`() {
        val handler1: OutputHandler by inject()
        val handler2: OutputHandler by inject()
        
        assertSame(handler1, handler2, "Default OutputHandler should be singleton")
    }
}