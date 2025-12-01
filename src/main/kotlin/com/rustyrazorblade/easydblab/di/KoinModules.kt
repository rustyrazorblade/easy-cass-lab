package com.rustyrazorblade.easydblab.di

import com.rustyrazorblade.easydblab.Context
import com.rustyrazorblade.easydblab.providers.aws.awsModule
import com.rustyrazorblade.easydblab.providers.docker.dockerModule
import com.rustyrazorblade.easydblab.providers.ssh.sshModule
import com.rustyrazorblade.easydblab.services.servicesModule
import org.koin.core.module.Module

/**
 * Central registry of all Koin modules for the application. Add new modules here as they are
 * created.
 */
object KoinModules {
    /**
     * Get all application modules for Koin initialization.
     *
     * @param context The Context instance to use for modules that require it
     */
    fun getAllModules(context: Context): List<Module> =
        listOf(
            outputModule,
            dockerModule,
            sshModule,
            awsModule,
            servicesModule,
            configurationModule,
        )
}
