package com.rustyrazorblade.easycasslab.docker

import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.DefaultUserIdProvider
import com.rustyrazorblade.easycasslab.Docker
import com.rustyrazorblade.easycasslab.DockerClientInterface
import com.rustyrazorblade.easycasslab.UserIdProvider

/**
 * Factory for creating Docker instances with different configurations.
 *
 * This factory provides convenient methods for creating Docker instances
 * with different output handlers, making it easy to switch between
 * console output, logging, or custom output handling.
 *
 * Example usage:
 * ```kotlin
 * // For console output (default)
 * val docker = DockerFactory.createConsoleDocker(context, dockerClient)
 *
 * // For logger output (background execution)
 * val docker = DockerFactory.createLoggerDocker(context, dockerClient, "MyContainer")
 *
 * // For testing with buffered output
 * val docker = DockerFactory.createBufferedDocker(context, dockerClient)
 * ```
 */
object DockerFactory {
    /**
     * Create a Docker instance with console output (default behavior).
     * Output will be written to stdout/stderr.
     *
     * @param context The execution context
     * @param dockerClient The Docker client interface
     * @param userIdProvider Optional custom user ID provider
     * @return Docker instance configured for console output
     */
    fun createConsoleDocker(
        context: Context,
        dockerClient: DockerClientInterface,
        userIdProvider: UserIdProvider = DefaultUserIdProvider(),
    ): Docker {
        return Docker(
            context = context,
            dockerClient = dockerClient,
            userIdProvider = userIdProvider,
        )
    }

    /**
     * Create a Docker instance with logger output.
     * Suitable for background execution and structured logging.
     *
     * @param context The execution context
     * @param dockerClient The Docker client interface
     * @param loggerName The name for the logger (default: "DockerContainer")
     * @param userIdProvider Optional custom user ID provider
     * @return Docker instance configured for logger output
     */
    fun createLoggerDocker(
        context: Context,
        dockerClient: DockerClientInterface,
        loggerName: String = "DockerContainer",
        userIdProvider: UserIdProvider = DefaultUserIdProvider(),
    ): Docker {
        return Docker(
            context = context,
            dockerClient = dockerClient,
            userIdProvider = userIdProvider,
        )
    }

    /**
     * Create a Docker instance with buffered output.
     * Useful for testing and programmatic output capture.
     *
     * @param context The execution context
     * @param dockerClient The Docker client interface
     * @param userIdProvider Optional custom user ID provider
     * @return Pair of Docker instance and the BufferedOutputHandler for accessing output
     */
    fun createBufferedDocker(
        context: Context,
        dockerClient: DockerClientInterface,
        userIdProvider: UserIdProvider = DefaultUserIdProvider(),
    ): Docker {
        return Docker(
            context = context,
            dockerClient = dockerClient,
            userIdProvider = userIdProvider,
        )
    }

    /**
     * Create a Docker instance with composite output.
     * Writes to multiple output handlers simultaneously.
     *
     * @param context The execution context
     * @param dockerClient The Docker client interface
     * @param outputHandlers The output handlers to use
     * @param userIdProvider Optional custom user ID provider
     * @return Docker instance configured with composite output
     */
    fun createCompositeDocker(
        context: Context,
        dockerClient: DockerClientInterface,
        userIdProvider: UserIdProvider = DefaultUserIdProvider(),
    ): Docker {
        return Docker(
            context = context,
            dockerClient = dockerClient,
            userIdProvider = userIdProvider,
        )
    }

    /**
     * Create a Docker instance with custom output handler.
     * Allows for completely custom output handling implementations.
     *
     * @param context The execution context
     * @param dockerClient The Docker client interface
     * @param outputHandler Custom output handler implementation
     * @param userIdProvider Optional custom user ID provider
     * @return Docker instance configured with custom output
     */
    fun createCustomDocker(
        context: Context,
        dockerClient: DockerClientInterface,
        userIdProvider: UserIdProvider = DefaultUserIdProvider(),
    ): Docker {
        return Docker(
            context = context,
            dockerClient = dockerClient,
            userIdProvider = userIdProvider,
        )
    }
}
