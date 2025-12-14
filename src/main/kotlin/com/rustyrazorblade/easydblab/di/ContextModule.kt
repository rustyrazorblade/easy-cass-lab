package com.rustyrazorblade.easydblab.di

import com.rustyrazorblade.easydblab.Context
import com.rustyrazorblade.easydblab.ContextFactory
import com.rustyrazorblade.easydblab.configuration.UserConfigProvider
import com.rustyrazorblade.easydblab.providers.ssh.DefaultSSHConfiguration
import com.rustyrazorblade.easydblab.providers.ssh.SSHConfiguration
import org.koin.dsl.module
import java.io.File

/**
 * Koin module for Context-related dependencies. Creates the ContextFactory and Context
 * internally using the standard user directory (~/.easy-db-lab/).
 */
val contextModule =
    module {
        // Create and provide the ContextFactory
        single {
            val easyDbLabUserDirectory = File(System.getProperty("user.home"), "/.easy-db-lab/")
            ContextFactory(easyDbLabUserDirectory)
        }

        // Provide Context instance for services that need it
        single<Context> { get<ContextFactory>().getDefault() }

        // Provide UserConfigProvider to manage user configuration loading
        single {
            val context = get<Context>()
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
