package com.rustyrazorblade.easydblab.services

import com.rustyrazorblade.easydblab.Constants
import com.rustyrazorblade.easydblab.configuration.ClusterHost
import com.rustyrazorblade.easydblab.kubernetes.ManifestApplier
import com.rustyrazorblade.easydblab.kubernetes.ProxiedKubernetesClientFactory
import com.rustyrazorblade.easydblab.output.OutputHandler
import com.rustyrazorblade.easydblab.proxy.SocksProxyService
import io.fabric8.kubernetes.client.KubernetesClient
import io.github.oshai.kotlinlogging.KotlinLogging
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/**
 * Service for managing Kubernetes resources on the K3s cluster.
 *
 * This service handles the application and management of Kubernetes manifests
 * for observability components (OTel collectors, Prometheus, Grafana) on the
 * K3s cluster. It connects via SOCKS proxy and uses the fabric8 Kubernetes client.
 *
 * All operations return Result types for explicit error handling.
 */
interface K8sService {
    /**
     * Applies Kubernetes manifests to the cluster.
     *
     * Reads manifest files from the specified path and applies them to the cluster
     * using the Kubernetes API via SOCKS proxy.
     *
     * @param controlHost The control node running the K3s server (used as gateway)
     * @param manifestPath Local path to a manifest file or directory containing K8s manifests
     * @return Result indicating success or failure
     */
    fun applyManifests(
        controlHost: ClusterHost,
        manifestPath: Path,
    ): Result<Unit>

    /**
     * Gets the status of observability pods in the cluster.
     *
     * @param controlHost The control node running the K3s server
     * @return Result containing pod status output or failure
     */
    fun getObservabilityStatus(controlHost: ClusterHost): Result<String>

    /**
     * Deletes the observability namespace and all its resources.
     *
     * @param controlHost The control node running the K3s server
     * @return Result indicating success or failure
     */
    fun deleteObservability(controlHost: ClusterHost): Result<Unit>

    /**
     * Waits for all pods in the observability namespace to be ready.
     *
     * @param controlHost The control node running the K3s server
     * @param timeoutSeconds Maximum time to wait for pods to be ready
     * @return Result indicating success or failure
     */
    fun waitForPodsReady(
        controlHost: ClusterHost,
        timeoutSeconds: Int = 120,
    ): Result<Unit>
}

/**
 * Default implementation of K8sService using fabric8 Kubernetes client via SOCKS proxy.
 *
 * This implementation connects to the K3s cluster through a SOCKS5 proxy tunnel,
 * allowing direct API access to private clusters.
 *
 * @property socksProxyService Service for managing the SOCKS5 proxy connection
 * @property outputHandler Handler for user-facing output messages
 */
class DefaultK8sService(
    private val socksProxyService: SocksProxyService,
    private val outputHandler: OutputHandler,
) : K8sService {
    private val log = KotlinLogging.logger {}

    companion object {
        private const val TIMEOUT_POLL_INTERVAL_MS = 5000L
    }

    /**
     * Creates a Kubernetes client connected via SOCKS proxy.
     */
    private fun createClient(controlHost: ClusterHost): KubernetesClient {
        log.info { "Creating K8s client for ${controlHost.alias} (${controlHost.privateIp})" }

        // Ensure proxy is running
        socksProxyService.ensureRunning(controlHost)
        val proxyPort = socksProxyService.getLocalPort()
        log.info { "SOCKS proxy running on localhost:$proxyPort" }

        // Create factory with current proxy port
        val clientFactory =
            ProxiedKubernetesClientFactory(
                proxyHost = "localhost",
                proxyPort = proxyPort,
            )

        // Create client using local kubeconfig
        val kubeconfigPath = Path.of(Constants.K3s.LOCAL_KUBECONFIG)
        log.info { "Using kubeconfig from $kubeconfigPath" }

        val client = clientFactory.createClient(kubeconfigPath)
        log.info { "K8s client created, master URL: ${client.configuration.masterUrl}" }

        return client
    }

    override fun applyManifests(
        controlHost: ClusterHost,
        manifestPath: Path,
    ): Result<Unit> =
        runCatching {
            log.info { "Applying K8s manifests from $manifestPath via SOCKS proxy" }

            createClient(controlHost).use { client ->
                outputHandler.handleMessage("Applying K8s manifests...")

                val pathFile = manifestPath.toFile()
                val manifestFiles =
                    if (pathFile.isFile) {
                        // Single file - apply just this file
                        listOf(pathFile)
                    } else {
                        // Directory - get all YAML files sorted lexicographically
                        // (namespace.yaml sorts first due to 'n' coming before 'o', 'p', 'g')
                        pathFile
                            .listFiles { file ->
                                file.extension == "yaml" || file.extension == "yml"
                            }?.sorted() ?: emptyList()
                    }

                if (manifestFiles.isEmpty()) {
                    throw IllegalStateException("No manifest files found at $manifestPath")
                }

                log.info { "Found ${manifestFiles.size} manifest files to apply" }
                manifestFiles.forEachIndexed { index, file ->
                    log.info { "  [$index] ${file.name}" }
                }

                for ((index, file) in manifestFiles.withIndex()) {
                    log.info { "Processing manifest ${index + 1}/${manifestFiles.size}: ${file.name}" }
                    ManifestApplier.applyManifest(client, file)
                }

                log.info { "All ${manifestFiles.size} manifests applied successfully" }
                outputHandler.handleMessage("K8s manifests applied successfully")
            }
        }

    override fun getObservabilityStatus(controlHost: ClusterHost): Result<String> =
        runCatching {
            log.debug { "Getting observability status via SOCKS proxy" }

            createClient(controlHost).use { client ->
                val pods =
                    client
                        .pods()
                        .inNamespace(Constants.K8s.NAMESPACE)
                        .list()

                // Format output similar to kubectl get pods -o wide
                val header = "NAME                          READY   STATUS    RESTARTS   AGE"
                val lines =
                    pods.items.map { pod ->
                        val name = pod.metadata?.name ?: "unknown"
                        val containerStatuses = pod.status?.containerStatuses ?: emptyList()
                        val readyContainers = containerStatuses.count { it.ready == true }
                        val totalContainers = containerStatuses.size.coerceAtLeast(1)
                        val ready = "$readyContainers/$totalContainers"
                        val status = pod.status?.phase ?: "Unknown"
                        val restarts = containerStatuses.sumOf { it.restartCount ?: 0 }
                        val age = "N/A" // Simplified - could calculate from creationTimestamp

                        "%-30s %-7s %-9s %-10d %s".format(name, ready, status, restarts, age)
                    }

                (listOf(header) + lines).joinToString("\n")
            }
        }

    override fun deleteObservability(controlHost: ClusterHost): Result<Unit> =
        runCatching {
            log.debug { "Deleting observability namespace via SOCKS proxy" }

            outputHandler.handleMessage("Deleting observability namespace...")

            createClient(controlHost).use { client ->
                val namespace = client.namespaces().withName(Constants.K8s.NAMESPACE).get()
                if (namespace != null) {
                    client.namespaces().withName(Constants.K8s.NAMESPACE).delete()
                    log.info { "Deleted namespace: ${Constants.K8s.NAMESPACE}" }
                } else {
                    log.info { "Namespace ${Constants.K8s.NAMESPACE} does not exist, nothing to delete" }
                }
            }

            outputHandler.handleMessage("Observability namespace deleted")
        }

    override fun waitForPodsReady(
        controlHost: ClusterHost,
        timeoutSeconds: Int,
    ): Result<Unit> =
        runCatching {
            log.debug { "Waiting for pods to be ready in ${Constants.K8s.NAMESPACE} namespace" }

            outputHandler.handleMessage("Waiting for observability pods to be ready...")

            createClient(controlHost).use { client ->
                val pods =
                    client
                        .pods()
                        .inNamespace(Constants.K8s.NAMESPACE)
                        .list()

                if (pods.items.isEmpty()) {
                    log.warn { "No pods found in ${Constants.K8s.NAMESPACE} namespace" }
                    return@runCatching
                }

                // Wait for each pod to be ready
                for (pod in pods.items) {
                    val podName = pod.metadata?.name ?: continue
                    log.debug { "Waiting for pod $podName to be ready" }

                    client
                        .pods()
                        .inNamespace(Constants.K8s.NAMESPACE)
                        .withName(podName)
                        .waitUntilCondition(
                            { p ->
                                p?.status?.conditions?.any {
                                    it.type == "Ready" && it.status == "True"
                                } == true
                            },
                            timeoutSeconds.toLong(),
                            TimeUnit.SECONDS,
                        )
                }
            }

            outputHandler.handleMessage("All observability pods are ready")
        }
}
