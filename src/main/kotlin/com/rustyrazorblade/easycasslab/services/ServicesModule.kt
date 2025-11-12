package com.rustyrazorblade.easycasslab.services

import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * Koin module for registering business services.
 *
 * This module provides service-layer components that encapsulate
 * business logic and orchestrate operations across multiple infrastructure
 * components (SSH, Docker, AWS, etc.).
 */
val servicesModule =
    module {
        factoryOf(::DefaultCassandraService) bind CassandraService::class
    }
