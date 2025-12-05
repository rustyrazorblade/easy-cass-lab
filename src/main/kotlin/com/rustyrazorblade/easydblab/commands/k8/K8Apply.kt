package com.rustyrazorblade.easydblab.commands.k8

import com.rustyrazorblade.easydblab.Context
import com.rustyrazorblade.easydblab.annotations.McpCommand
import com.rustyrazorblade.easydblab.annotations.RequireProfileSetup
import com.rustyrazorblade.easydblab.commands.PicoBaseCommand
import com.rustyrazorblade.easydblab.configuration.ServerType
import com.rustyrazorblade.easydblab.output.displayObservabilityAccess
import com.rustyrazorblade.easydblab.services.K8sService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.koin.core.component.inject
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import java.nio.file.Path

/**
 * Apply observability stack to the K8s cluster.
 *
 * This command deploys the observability infrastructure (OTel collectors,
 * Prometheus, Grafana) to the K3s cluster running on the lab environment.
 *
 * The observability stack includes:
 * - OTel collector DaemonSet on control node (aggregator)
 * - OTel collector DaemonSet on worker nodes (forwarders)
 * - Prometheus for metrics storage and querying
 * - Grafana with pre-configured dashboards
 */
@McpCommand
@RequireProfileSetup
@Command(
    name = "apply",
    description = ["Apply observability stack to K8s cluster"],
)
class K8Apply(
    context: Context,
) : PicoBaseCommand(context) {
    private val log = KotlinLogging.logger {}
    private val k8sService: K8sService by inject()

    @Suppress("MagicNumber")
    @Option(
        names = ["--timeout"],
        description = ["Timeout in seconds to wait for pods to be ready (default: 120)"],
    )
    var timeoutSeconds: Int = 120

    @Option(
        names = ["--skip-wait"],
        description = ["Skip waiting for pods to be ready"],
    )
    var skipWait: Boolean = false

    @Option(
        names = ["-f", "--file"],
        description = ["Path to manifest file or directory (default: core observability stack)"],
    )
    var manifestPath: Path? = null

    companion object {
        private const val K8S_CORE_MANIFEST_DIR = "k8s/core"
    }

    override fun execute() {
        // Get control node from cluster state (ClusterHost for SOCKS proxy)
        val controlHosts = clusterState.hosts[ServerType.Control]
        if (controlHosts.isNullOrEmpty()) {
            error("No control nodes found. Please ensure the environment is running.")
        }
        val controlNode = controlHosts.first()
        log.debug { "Using control node: ${controlNode.alias} (${controlNode.publicIp})" }

        // Determine manifest path - use provided path or default to core manifests
        val pathToApply = manifestPath ?: Path.of(K8S_CORE_MANIFEST_DIR)
        log.info { "Applying manifests from: $pathToApply" }

        // Apply manifests to cluster
        k8sService
            .applyManifests(controlNode, pathToApply)
            .getOrElse { exception ->
                error("Failed to apply K8s manifests: ${exception.message}")
            }

        // Wait for pods to be ready
        if (!skipWait) {
            k8sService
                .waitForPodsReady(controlNode, timeoutSeconds)
                .getOrElse { exception ->
                    outputHandler.handleError("Warning: Pods may not be ready: ${exception.message}")
                    outputHandler.handleMessage("You can check status with: kubectl get pods -n observability")
                }
        }

        // Display access information
        outputHandler.handleMessage("")
        outputHandler.handleMessage("Observability stack deployed successfully!")
        outputHandler.displayObservabilityAccess(controlNode.privateIp)
    }
}
