package com.rustyrazorblade.easycasslab.providers.docker

import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.DefaultUserIdProvider
import com.rustyrazorblade.easycasslab.Docker
import com.rustyrazorblade.easycasslab.UserIdProvider
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * Koin module for Docker-related dependency injection.
 *
 * Provides:
 * - DockerClientProvider as a singleton (expensive to create)
 * - UserIdProvider as a singleton (stateless utility)
 * - Docker instances as factory (new instance per injection with state)
 */
val dockerModule =
    module {
        // Docker client provider - singleton because Docker client is expensive to create
        singleOf(::DefaultDockerClientProvider) bind DockerClientProvider::class

        // User ID provider - singleton because it's a stateless utility
        singleOf(::DefaultUserIdProvider) bind UserIdProvider::class

        // Docker instances - factory because each instance maintains its own state (volumes, env)
        factory { (context: Context) ->
            Docker(
                context = context,
                dockerClient = get<DockerClientProvider>().getDockerClient(),
                userIdProvider = get(),
                outputHandler = get(),
            )
        }

        // Docker instances with specific output handlers
        factory(named("dockerWithLogger")) { (context: Context) ->
            Docker(
                context = context,
                dockerClient = get<DockerClientProvider>().getDockerClient(),
                userIdProvider = get(),
                outputHandler = get(named("logger")),
            )
        }
    }
