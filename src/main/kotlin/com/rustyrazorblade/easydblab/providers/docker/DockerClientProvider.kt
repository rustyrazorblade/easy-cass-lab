package com.rustyrazorblade.easydblab.providers.docker

import com.rustyrazorblade.easydblab.DockerClientInterface

/**
 * Provider interface for Docker client instances.
 * Implementations are responsible for creating and configuring Docker clients.
 */
interface DockerClientProvider {
    /**
     * Get a configured Docker client instance.
     *
     * @return A configured DockerClientInterface ready for use
     */
    fun getDockerClient(): DockerClientInterface
}
