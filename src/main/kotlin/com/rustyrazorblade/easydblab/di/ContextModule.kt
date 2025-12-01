package com.rustyrazorblade.easydblab.di

import com.rustyrazorblade.easydblab.ContextFactory
import com.rustyrazorblade.easydblab.configuration.UserConfigProvider
import com.rustyrazorblade.easydblab.providers.ssh.DefaultSSHConfiguration
import com.rustyrazorblade.easydblab.providers.ssh.SSHConfiguration
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
            val context = contextFactory.getDefault()
            UserConfigProvider(context.profileDir)
        }

        // Provide User configuration via UserConfigProvider
        single { get<UserConfigProvider>().getUserConfig() }

        // Provide SSH configuration from the user config
        single<SSHConfiguration> {
            val user = get<com.rustyrazorblade.easydblab.configuration.User>()
            DefaultSSHConfiguration(
                keyPath = user.sshKeyPath,
            )
        }
    }
