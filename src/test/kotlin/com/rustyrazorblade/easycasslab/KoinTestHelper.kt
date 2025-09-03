package com.rustyrazorblade.easycasslab

import com.rustyrazorblade.easycasslab.output.BufferedOutputHandler
import com.rustyrazorblade.easycasslab.output.ConsoleOutputHandler
import com.rustyrazorblade.easycasslab.output.OutputHandler
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Helper object for setting up Koin context in tests
 */
object KoinTestHelper {
    /**
     * Test module that provides test-specific OutputHandler implementations
     */
    val testModule =
        module {
            // Default output handler for tests - use BufferedOutputHandler for testing
            factory<OutputHandler> { BufferedOutputHandler() }

            // Named output handlers for specific test cases
            factory<OutputHandler>(named("console")) { ConsoleOutputHandler() }
            factory<OutputHandler>(named("logger")) { com.rustyrazorblade.easycasslab.output.LoggerOutputHandler("Test") }
            factory<OutputHandler>(named("buffered")) { BufferedOutputHandler() }
        }

    /**
     * Start Koin with test configuration
     */
    fun startKoin() {
        if (GlobalContext.getOrNull() == null) {
            startKoin {
                modules(testModule)
            }
        }
    }

    /**
     * Stop Koin context
     */
    fun stopKoin() {
        org.koin.core.context.stopKoin()
    }

    /**
     * Reset Koin context - stops and restarts with clean state
     */
    fun resetKoin() {
        org.koin.core.context.stopKoin()
        startKoin()
    }
}
