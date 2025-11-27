package com.rustyrazorblade.easycasslab.di

import com.rustyrazorblade.easycasslab.configuration.ClusterStateManager
import org.koin.dsl.module

/**
 * Koin module for configuration-related dependencies.
 */
val configurationModule =
    module {
        single { ClusterStateManager() }
    }
