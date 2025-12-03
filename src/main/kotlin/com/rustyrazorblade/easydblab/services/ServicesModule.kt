package com.rustyrazorblade.easydblab.services

import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
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
        factoryOf(::DefaultEasyStressService) bind EasyStressService::class
        factoryOf(::DefaultK3sService) bind K3sService::class
        factoryOf(::DefaultK3sAgentService) bind K3sAgentService::class
        factoryOf(::DefaultK8sService) bind K8sService::class
        factoryOf(::DefaultSidecarService) bind SidecarService::class
        singleOf(::HostOperationsService)
    }
