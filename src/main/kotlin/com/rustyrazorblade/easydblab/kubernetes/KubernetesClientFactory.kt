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
    companion object {
        private const val CONNECTION_TIMEOUT_MS = 30000
        private const val REQUEST_TIMEOUT_MS = 60000
    }

    override fun createClient(kubeconfigPath: Path): KubernetesClient {
        // Load kubeconfig from file
        val kubeconfigContent = kubeconfigPath.toFile().readText()
        val config = Config.fromKubeconfig(kubeconfigContent)

        // Configure SOCKS5 proxy for HTTPS (K8s API uses HTTPS on port 6443)
        val proxyUrl = "socks5://$proxyHost:$proxyPort"
        config.httpsProxy = proxyUrl

        // Increase timeouts for SOCKS proxy connections (default 10s is too short)
        config.connectionTimeout = CONNECTION_TIMEOUT_MS
        config.requestTimeout = REQUEST_TIMEOUT_MS

        return KubernetesClientBuilder()
            .withConfig(config)
            .build()
    }
}
