package com.rustyrazorblade.easycasslab.di

import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.providers.ssh.DefaultSSHConfiguration
import com.rustyrazorblade.easycasslab.providers.ssh.SSHConfiguration
import org.koin.dsl.module

/**
 * Koin module for Context-related dependencies.
 * This module requires a Context instance to be provided when creating the Koin application.
 */
fun contextModule(context: Context) = module {
    // Provide the context itself
    single { context }
    
    // Provide SSH configuration from the user config
    single<SSHConfiguration> {
        DefaultSSHConfiguration(
            keyPath = context.userConfig.sshKeyPath
        )
    }
}