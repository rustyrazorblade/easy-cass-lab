package com.rustyrazorblade.easycasslab.di

import com.rustyrazorblade.easycasslab.ContextFactory
import com.rustyrazorblade.easycasslab.configuration.UserConfigProvider
import com.rustyrazorblade.easycasslab.output.OutputHandler
import com.rustyrazorblade.easycasslab.providers.ssh.DefaultSSHConfiguration
import com.rustyrazorblade.easycasslab.providers.ssh.SSHConfiguration
import org.koin.dsl.module

/**
 * Koin module for Context-related dependencies. This module requires a ContextFactory instance to
 * be provided when creating the Koin application.
 */
fun contextModule(contextFactory: ContextFactory) =
    module {
        // Provide the factory itself
        single { contextFactory }

        // Provide UserConfigProvider to manage user configuration loading
        single {
            val outputHandler = get<OutputHandler>()
            val context = contextFactory.getDefault()
            UserConfigProvider(context.profileDir, outputHandler)
        }

        // Provide User configuration via UserConfigProvider
        single { get<UserConfigProvider>().getUserConfig() }

        // Provide SSH configuration from the user config
        single<SSHConfiguration> {
            val user = get<com.rustyrazorblade.easycasslab.configuration.User>()
            DefaultSSHConfiguration(
                keyPath = user.sshKeyPath,
            )
        }
    }
