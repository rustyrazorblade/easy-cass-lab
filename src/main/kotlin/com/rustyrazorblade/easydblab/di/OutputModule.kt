package com.rustyrazorblade.easydblab.di

import com.rustyrazorblade.easydblab.output.ConsoleOutputHandler
import com.rustyrazorblade.easydblab.output.LoggerOutputHandler
import com.rustyrazorblade.easydblab.output.OutputHandler
import com.rustyrazorblade.easydblab.output.SubscribableOutputHandler
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Koin module for output handling dependency injection.
 *
 * Uses a pub/sub architecture where:
 * - SubscribableOutputHandler is the primary handler with logger as default
 * - CLI commands subscribe a ConsoleOutputHandler during execution
 * - MCP server subscribes a McpProgressNotifier during tool execution
 *
 * Named handlers are available for specific use cases.
 */
val outputModule =
    module {
        // Default output handler - logger only
        // Console output is subscribed dynamically during CLI command execution
        single<OutputHandler>(named("default")) {
            LoggerOutputHandler("EasyDBLab")
        }

        // SubscribableOutputHandler wraps default and allows dynamic subscription
        // CLI subscribes ConsoleOutputHandler, MCP subscribes McpProgressNotifier
        single<SubscribableOutputHandler> {
            SubscribableOutputHandler(get(named("default")))
        }

        // Primary OutputHandler binding - delegates to SubscribableOutputHandler
        single<OutputHandler> { get<SubscribableOutputHandler>() }

        // Named output handlers for specific use cases
        single<OutputHandler>(named("console")) { ConsoleOutputHandler() }
        single<OutputHandler>(named("logger")) { LoggerOutputHandler("Application") }
    }
