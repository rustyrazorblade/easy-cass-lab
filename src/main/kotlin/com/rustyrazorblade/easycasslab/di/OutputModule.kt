package com.rustyrazorblade.easycasslab.di

import com.rustyrazorblade.easycasslab.output.CompositeOutputHandler
import com.rustyrazorblade.easycasslab.output.ConsoleOutputHandler
import com.rustyrazorblade.easycasslab.output.LoggerOutputHandler
import com.rustyrazorblade.easycasslab.output.OutputHandler
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Koin module for output handling dependency injection.
 *
 * Provides OutputHandler implementations for different use cases:
 * - Composite output (default) - combines console and logger output
 * - Console output (named instance)
 * - Logger output (named instance)
 * - Named instances for specific use cases
 */
val outputModule =
    module {
        // Default output handler - composite with console and logger
        single<OutputHandler> {
            CompositeOutputHandler(
                LoggerOutputHandler("EasyCassLab"),
                ConsoleOutputHandler(),
            )
        }

        // Named output handlers for specific use cases
        single<OutputHandler>(named("console")) { ConsoleOutputHandler() }
        single<OutputHandler>(named("logger")) { LoggerOutputHandler("Application") }
    }
