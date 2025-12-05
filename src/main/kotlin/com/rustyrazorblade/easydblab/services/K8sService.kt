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

    /**
     * Waits for all pods in a specific namespace to be ready.
     *
     * @param controlHost The control node running the K3s server
     * @param timeoutSeconds Maximum time to wait for pods to be ready
     * @param namespace The namespace to check pods in
     * @return Result indicating success or failure
     */
    fun waitForPodsReady(
        controlHost: ClusterHost,
        timeoutSeconds: Int = 120,
        namespace: String,
    ): Result<Unit>

    /**
     * Gets the status of pods in a specific namespace.
     *
     * @param controlHost The control node running the K3s server
     * @param namespace The namespace to get pod status for
     * @return Result containing pod status output or failure
     */
    fun getNamespaceStatus(
        controlHost: ClusterHost,
        namespace: String,
    ): Result<String>

    /**
     * Deletes a namespace and all its resources.
     *
     * @param controlHost The control node running the K3s server
     * @param namespace The namespace to delete
     * @return Result indicating success or failure
     */
    fun deleteNamespace(
        controlHost: ClusterHost,
        namespace: String,
    ): Result<Unit>

    /**
     * Deletes Kubernetes resources by label selector.
     *
     * Deletes all resources matching the specified label in the given namespace.
     * Deletion order: StatefulSets, Deployments, Services, ConfigMaps, Secrets, PVCs.
     *
     * @param controlHost The control node running the K3s server
     * @param namespace The namespace to delete resources from
     * @param labelKey The label key to match (e.g., "app.kubernetes.io/name")
     * @param labelValues List of label values to match
     * @return Result indicating success or failure
     */
    fun deleteResourcesByLabel(
        controlHost: ClusterHost,
        namespace: String,
        labelKey: String,
        labelValues: List<String>,
    ): Result<Unit>

    /**
     * Creates a Kubernetes secret for ClickHouse S3 storage configuration.
     *
     * This creates a secret containing only the S3 endpoint URL. AWS credentials
     * are obtained from EC2 instance IAM roles via the instance metadata service.
     *
     * @param controlHost The control node running the K3s server
     * @param namespace The namespace to create the secret in
     * @param region AWS region for the S3 bucket
     * @param bucket S3 bucket name
     * @return Result indicating success or failure
     */
    fun createClickHouseS3Secret(
        controlHost: ClusterHost,
        namespace: String,
        region: String,
        bucket: String,
    ): Result<Unit>

    /**
     * Applies a single Kubernetes manifest from resources.
     *
     * @param controlHost The control node running the K3s server
     * @param resourcePath Path to the manifest resource (relative to k8s/ directory)
     * @return Result indicating success or failure
     */
    fun applyManifestFromResources(
        controlHost: ClusterHost,
        resourcePath: String,
    ): Result<Unit>

    /**
     * Scales a StatefulSet to the specified number of replicas.
     *
     * @param controlHost The control node running the K3s server
     * @param namespace The namespace containing the StatefulSet
     * @param statefulSetName The name of the StatefulSet to scale
     * @param replicas The desired number of replicas
     * @return Result indicating success or failure
     */
    fun scaleStatefulSet(
        controlHost: ClusterHost,
        namespace: String,
        statefulSetName: String,
        replicas: Int,
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
                        // Files use number prefixes (00-, 10-, etc.) to ensure correct ordering
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

    override fun waitForPodsReady(
        controlHost: ClusterHost,
        timeoutSeconds: Int,
        namespace: String,
    ): Result<Unit> =
        runCatching {
            log.debug { "Waiting for pods to be ready in $namespace namespace" }

            outputHandler.handleMessage("Waiting for pods in $namespace to be ready...")

            createClient(controlHost).use { client ->
                val pods =
                    client
                        .pods()
                        .inNamespace(namespace)
                        .list()

                if (pods.items.isEmpty()) {
                    log.warn { "No pods found in $namespace namespace" }
                    return@runCatching
                }

                // Wait for each pod to be ready
                for (pod in pods.items) {
                    val podName = pod.metadata?.name ?: continue
                    log.debug { "Waiting for pod $podName to be ready" }

                    client
                        .pods()
                        .inNamespace(namespace)
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

            outputHandler.handleMessage("All pods in $namespace are ready")
        }

    override fun getNamespaceStatus(
        controlHost: ClusterHost,
        namespace: String,
    ): Result<String> =
        runCatching {
            log.debug { "Getting status for namespace $namespace via SOCKS proxy" }

            createClient(controlHost).use { client ->
                val pods =
                    client
                        .pods()
                        .inNamespace(namespace)
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

    override fun deleteNamespace(
        controlHost: ClusterHost,
        namespace: String,
    ): Result<Unit> =
        runCatching {
            log.debug { "Deleting namespace $namespace via SOCKS proxy" }

            outputHandler.handleMessage("Deleting $namespace namespace...")

            createClient(controlHost).use { client ->
                val ns = client.namespaces().withName(namespace).get()
                if (ns != null) {
                    client.namespaces().withName(namespace).delete()
                    log.info { "Deleted namespace: $namespace" }
                } else {
                    log.info { "Namespace $namespace does not exist, nothing to delete" }
                }
            }

            outputHandler.handleMessage("Namespace $namespace deleted")
        }

    override fun deleteResourcesByLabel(
        controlHost: ClusterHost,
        namespace: String,
        labelKey: String,
        labelValues: List<String>,
    ): Result<Unit> =
        runCatching {
            log.info { "Deleting resources with $labelKey in $labelValues from namespace $namespace" }

            outputHandler.handleMessage("Deleting resources with label $labelKey...")

            createClient(controlHost).use { client ->
                // Build label selector for "key in (value1, value2, ...)"
                val labelSelector = "$labelKey in (${labelValues.joinToString(",")})"
                log.debug { "Using label selector: $labelSelector" }

                // Delete StatefulSets first (they manage pods)
                val statefulSets =
                    client
                        .apps()
                        .statefulSets()
                        .inNamespace(namespace)
                        .withLabelSelector(labelSelector)
                        .list()
                statefulSets.items.forEach { sts ->
                    val name = sts.metadata?.name ?: return@forEach
                    log.info { "Deleting StatefulSet: $name" }
                    client
                        .apps()
                        .statefulSets()
                        .inNamespace(namespace)
                        .withName(name)
                        .delete()
                }

                // Delete Deployments
                val deployments =
                    client
                        .apps()
                        .deployments()
                        .inNamespace(namespace)
                        .withLabelSelector(labelSelector)
                        .list()
                deployments.items.forEach { deploy ->
                    val name = deploy.metadata?.name ?: return@forEach
                    log.info { "Deleting Deployment: $name" }
                    client
                        .apps()
                        .deployments()
                        .inNamespace(namespace)
                        .withName(name)
                        .delete()
                }

                // Delete Services
                val services =
                    client
                        .services()
                        .inNamespace(namespace)
                        .withLabelSelector(labelSelector)
                        .list()
                services.items.forEach { svc ->
                    val name = svc.metadata?.name ?: return@forEach
                    log.info { "Deleting Service: $name" }
                    client
                        .services()
                        .inNamespace(namespace)
                        .withName(name)
                        .delete()
                }

                // Delete ConfigMaps
                val configMaps =
                    client
                        .configMaps()
                        .inNamespace(namespace)
                        .withLabelSelector(labelSelector)
                        .list()
                configMaps.items.forEach { cm ->
                    val name = cm.metadata?.name ?: return@forEach
                    log.info { "Deleting ConfigMap: $name" }
                    client
                        .configMaps()
                        .inNamespace(namespace)
                        .withName(name)
                        .delete()
                }

                // Delete Secrets
                val secrets =
                    client
                        .secrets()
                        .inNamespace(namespace)
                        .withLabelSelector(labelSelector)
                        .list()
                secrets.items.forEach { secret ->
                    val name = secret.metadata?.name ?: return@forEach
                    log.info { "Deleting Secret: $name" }
                    client
                        .secrets()
                        .inNamespace(namespace)
                        .withName(name)
                        .delete()
                }

                // Delete PVCs (persistent volume claims)
                val pvcs =
                    client
                        .persistentVolumeClaims()
                        .inNamespace(namespace)
                        .withLabelSelector(labelSelector)
                        .list()
                pvcs.items.forEach { pvc ->
                    val name = pvc.metadata?.name ?: return@forEach
                    log.info { "Deleting PVC: $name" }
                    client
                        .persistentVolumeClaims()
                        .inNamespace(namespace)
                        .withName(name)
                        .delete()
                }

                log.info { "All resources with label $labelKey deleted" }
            }

            outputHandler.handleMessage("Resources deleted successfully")
        }

    override fun createClickHouseS3Secret(
        controlHost: ClusterHost,
        namespace: String,
        region: String,
        bucket: String,
    ): Result<Unit> =
        runCatching {
            log.info { "Creating ClickHouse S3 secret in namespace $namespace" }

            // Build the S3 endpoint URL
            val s3Endpoint = "https://$bucket.s3.$region.amazonaws.com/clickhouse/"
            log.info { "S3 endpoint: $s3Endpoint" }

            createClient(controlHost).use { client ->
                // Check if secret already exists and delete it
                val existingSecret =
                    client
                        .secrets()
                        .inNamespace(namespace)
                        .withName(Constants.ClickHouse.S3_SECRET_NAME)
                        .get()

                if (existingSecret != null) {
                    log.info { "Deleting existing secret ${Constants.ClickHouse.S3_SECRET_NAME}" }
                    client
                        .secrets()
                        .inNamespace(namespace)
                        .withName(Constants.ClickHouse.S3_SECRET_NAME)
                        .delete()
                }

                // Create the secret with S3 endpoint
                val secret =
                    io.fabric8.kubernetes.api.model
                        .SecretBuilder()
                        .withNewMetadata()
                        .withName(Constants.ClickHouse.S3_SECRET_NAME)
                        .withNamespace(namespace)
                        .addToLabels("app.kubernetes.io/name", "clickhouse-server")
                        .endMetadata()
                        .withType("Opaque")
                        .addToStringData("CLICKHOUSE_S3_ENDPOINT", s3Endpoint)
                        .build()

                client
                    .secrets()
                    .inNamespace(namespace)
                    .resource(secret)
                    .create()
                log.info { "Created secret ${Constants.ClickHouse.S3_SECRET_NAME}" }
            }

            outputHandler.handleMessage("Created ClickHouse S3 secret")
        }

    override fun applyManifestFromResources(
        controlHost: ClusterHost,
        resourcePath: String,
    ): Result<Unit> =
        runCatching {
            log.info { "Applying K8s manifest from resources: $resourcePath" }

            createClient(controlHost).use { client ->
                // Load manifest from resources
                val resourceStream =
                    this::class.java.getResourceAsStream("/com/rustyrazorblade/easydblab/commands/$resourcePath")
                        ?: throw IllegalStateException("Resource not found: $resourcePath")

                // Create temp file from resource
                val tempFile =
                    kotlin.io.path
                        .createTempFile("manifest", ".yaml")
                        .toFile()
                try {
                    resourceStream.use { input ->
                        tempFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }

                    log.info { "Applying manifest: ${tempFile.name}" }
                    ManifestApplier.applyManifest(client, tempFile)
                    log.info { "Manifest applied successfully" }
                } finally {
                    tempFile.delete()
                }
            }

            outputHandler.handleMessage("Manifest applied: $resourcePath")
        }

    override fun scaleStatefulSet(
        controlHost: ClusterHost,
        namespace: String,
        statefulSetName: String,
        replicas: Int,
    ): Result<Unit> =
        runCatching {
            log.info { "Scaling StatefulSet $statefulSetName in namespace $namespace to $replicas replicas" }

            createClient(controlHost).use { client ->
                client
                    .apps()
                    .statefulSets()
                    .inNamespace(namespace)
                    .withName(statefulSetName)
                    .scale(replicas)

                log.info { "StatefulSet $statefulSetName scaled to $replicas replicas" }
            }

            outputHandler.handleMessage("Scaled $statefulSetName to $replicas replicas")
        }
}
