package com.rustyrazorblade.easycasslab.di

import com.rustyrazorblade.easycasslab.providers.docker.dockerModule
import com.rustyrazorblade.easycasslab.providers.ssh.sshModule
import org.koin.core.module.Module

/**
 * Central registry of all Koin modules for the application.
 * Add new modules here as they are created.
 */
object KoinModules {
    /**
     * Get all application modules for Koin initialization.
     */
    fun getAllModules(): List<Module> =
        listOf(
            outputModule,
            dockerModule,
            sshModule,
            // Add more modules here as the refactoring progresses
            // e.g., awsModule, configurationModule
        )
}
