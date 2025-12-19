package com.rustyrazorblade.easydblab.di

import com.rustyrazorblade.easydblab.ConsolePrompter
import com.rustyrazorblade.easydblab.Prompter
import org.koin.dsl.module

/**
 * Koin module for user prompting functionality.
 * Provides the default ConsolePrompter for production use.
 * Tests can override this with a mock Prompter implementation.
 */
val prompterModule =
    module {
        single<Prompter> { ConsolePrompter() }
    }
