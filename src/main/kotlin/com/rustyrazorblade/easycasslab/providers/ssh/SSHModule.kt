package com.rustyrazorblade.easycasslab.providers.ssh

import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * Koin module for SSH-related dependency injection.
 *
 * Provides:
 * - SSHConnectionProvider as a singleton (manages connection pool)
 * - RemoteOperationsService as a factory (stateless operations)
 *
 * Note: SSHConfiguration must be provided by another module (e.g., contextModule)
 */
val sshModule =
    module {
        // SSH connection provider - singleton because it manages a connection pool
        singleOf(::DefaultSSHConnectionProvider) bind SSHConnectionProvider::class

        // Remote operations service - factory because it's stateless
        factoryOf(::DefaultRemoteOperationsService) bind RemoteOperationsService::class
    }
