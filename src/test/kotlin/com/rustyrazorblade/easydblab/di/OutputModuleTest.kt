package com.rustyrazorblade.easydblab.di

import com.rustyrazorblade.easydblab.output.ConsoleOutputHandler
import com.rustyrazorblade.easydblab.output.LoggerOutputHandler
import com.rustyrazorblade.easydblab.output.OutputHandler
import com.rustyrazorblade.easydblab.output.SubscribableOutputHandler
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.qualifier.named
import org.koin.test.KoinTest
import org.koin.test.inject

/**
 * Tests for OutputModule pub/sub architecture.
 *
 * The architecture is:
 * - SubscribableOutputHandler is the primary OutputHandler binding
 * - It wraps LoggerOutputHandler as the default (always logs)
 * - Console output is dynamically subscribed during CLI execution
 * - MCP progress notifier is dynamically subscribed during tool execution
 */
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
    fun `primary OutputHandler should be SubscribableOutputHandler`() {
        val handler: OutputHandler by inject()

        assertThat(handler)
            .isInstanceOf(SubscribableOutputHandler::class.java)
    }

    @Test
    fun `default named handler should be LoggerOutputHandler`() {
        val handler: OutputHandler by inject(qualifier = named("default"))

        assertThat(handler)
            .isInstanceOf(LoggerOutputHandler::class.java)
    }

    @Test
    fun `named console handler should be ConsoleOutputHandler instance`() {
        val consoleHandler: OutputHandler by inject(qualifier = named("console"))

        assertThat(consoleHandler)
            .isInstanceOf(ConsoleOutputHandler::class.java)
    }

    @Test
    fun `named logger handler should be LoggerOutputHandler instance`() {
        val loggerHandler: OutputHandler by inject(qualifier = named("logger"))

        assertThat(loggerHandler)
            .isInstanceOf(LoggerOutputHandler::class.java)
    }

    @Test
    fun `SubscribableOutputHandler should be singleton`() {
        val handler1: OutputHandler by inject()
        val handler2: OutputHandler by inject()

        assertThat(handler1).isSameAs(handler2)
    }

    @Test
    fun `SubscribableOutputHandler can be injected directly`() {
        val subscribable: SubscribableOutputHandler by inject()
        val handler: OutputHandler by inject()

        assertThat(subscribable).isSameAs(handler)
    }
}
