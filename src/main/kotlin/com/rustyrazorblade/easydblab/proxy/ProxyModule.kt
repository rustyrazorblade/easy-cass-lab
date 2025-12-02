package com.rustyrazorblade.easydblab.proxy

import org.koin.dsl.module

/**
 * Koin module for proxy-related dependency injection.
 *
 * Provides:
 * - SocksProxyService as a singleton (manages proxy lifecycle across requests in server mode)
 *
 * Note: SSHConnectionProvider must be provided by sshModule
 */
val proxyModule =
    module {
        // SOCKS proxy service - singleton because it maintains proxy state across requests
        // in server mode. In CLI mode, it's started/stopped per command.
        // Port is dynamically selected at startup to avoid conflicts.
        single<SocksProxyService> { MinaSocksProxyService(get()) }
    }
