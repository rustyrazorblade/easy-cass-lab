package com.rustyrazorblade.easydblab.kubernetes

import io.fabric8.kubernetes.client.Config
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientBuilder
import java.nio.file.Path

/**
 * Factory interface for creating Kubernetes clients.
 *
 * Abstracts the client creation to allow different configurations
 * (direct connection, SOCKS proxy, etc.)
 */
interface KubernetesClientFactory {
    /**
     * Create a Kubernetes client from a kubeconfig file.
     *
     * @param kubeconfigPath Path to the kubeconfig file
     * @return Configured KubernetesClient ready for API calls
     */
    fun createClient(kubeconfigPath: Path): KubernetesClient
}

/**
 * Kubernetes client factory that configures SOCKS5 proxy for access to private clusters.
 *
 * This is used when accessing K3s clusters running on private IPs through an SSH tunnel.
 *
 * @property proxyHost The SOCKS5 proxy host (typically "localhost")
 * @property proxyPort The SOCKS5 proxy port
 */
class ProxiedKubernetesClientFactory(
    private val proxyHost: String = "localhost",
    private val proxyPort: Int,
) : KubernetesClientFactory {
    override fun createClient(kubeconfigPath: Path): KubernetesClient {
        // Load kubeconfig from file
        val kubeconfigContent = kubeconfigPath.toFile().readText()
        val config = Config.fromKubeconfig(kubeconfigContent)

        // Configure SOCKS5 proxy
        config.httpProxy = "socks5://$proxyHost:$proxyPort"

        return KubernetesClientBuilder()
            .withConfig(config)
            .build()
    }
}
