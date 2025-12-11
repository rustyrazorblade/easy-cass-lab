package com.rustyrazorblade.easydblab.commands.clickhouse

import com.rustyrazorblade.easydblab.Constants
import com.rustyrazorblade.easydblab.Context
import com.rustyrazorblade.easydblab.annotations.McpCommand
import com.rustyrazorblade.easydblab.annotations.RequireProfileSetup
import com.rustyrazorblade.easydblab.commands.PicoBaseCommand
import com.rustyrazorblade.easydblab.configuration.ServerType
import com.rustyrazorblade.easydblab.configuration.User
import com.rustyrazorblade.easydblab.services.K8sService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.koin.core.component.inject
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import java.nio.file.Path

/**
 * Deploy ClickHouse cluster to K8s.
 *
 * This command deploys a ClickHouse cluster with ClickHouse Keeper
 * for distributed coordination.
 *
 * Storage policies available for tables:
 * - 'local': Local disk storage (default)
 * - 's3_main': S3 storage with local cache
 *
 * Example: CREATE TABLE t (...) SETTINGS storage_policy = 's3_main';
 */
@McpCommand
@RequireProfileSetup
@Command(
    name = "start",
    description = ["Deploy ClickHouse cluster to K8s"],
)
class ClickHouseStart(
    context: Context,
) : PicoBaseCommand(context) {
    private val log = KotlinLogging.logger {}
    private val k8sService: K8sService by inject()
    private val userConfig: User by inject()

    companion object {
        private const val K8S_MANIFEST_BASE = "k8s/clickhouse"
        private const val DEFAULT_TIMEOUT_SECONDS = 300
    }

    @Option(
        names = ["--timeout"],
        description = ["Timeout in seconds to wait for pods to be ready (default: 300)"],
    )
    var timeoutSeconds: Int = DEFAULT_TIMEOUT_SECONDS

    @Option(
        names = ["--skip-wait"],
        description = ["Skip waiting for pods to be ready"],
    )
    var skipWait: Boolean = false

    @Option(
        names = ["--replicas"],
        description = ["Number of ClickHouse server replicas (default: number of db nodes)"],
    )
    var replicas: Int? = null

    override fun execute() {
        // Get control node from cluster state
        val controlHosts = clusterState.hosts[ServerType.Control]
        if (controlHosts.isNullOrEmpty()) {
            error("No control nodes found. Please ensure the environment is running.")
        }
        val controlNode = controlHosts.first()
        log.debug { "Using control node: ${controlNode.alias} (${controlNode.publicIp})" }

        // Get db nodes for ClickHouse deployment
        val dbHosts = clusterState.hosts[ServerType.Cassandra]
        if (dbHosts.isNullOrEmpty()) {
            error("No db nodes found. Please ensure the environment is running.")
        }

        // Validate minimum node count for ClickHouse Keeper coordination
        if (dbHosts.size < Constants.ClickHouse.MINIMUM_NODES_REQUIRED) {
            error(
                "ClickHouse requires at least ${Constants.ClickHouse.MINIMUM_NODES_REQUIRED} nodes " +
                    "for Keeper coordination. Found ${dbHosts.size} node(s).",
            )
        }

        // Determine replica count: use provided value or default to number of db nodes
        val actualReplicas = replicas ?: dbHosts.size

        outputHandler.handleMessage("Deploying ClickHouse cluster with $actualReplicas replicas...")

        // Create S3 secret with endpoint URL (always created for s3_main policy)
        val bucket = clusterState.s3Bucket
        if (!bucket.isNullOrBlank()) {
            log.info { "Creating S3 secret for s3_main storage policy" }
            k8sService
                .createClickHouseS3Secret(
                    controlNode,
                    Constants.ClickHouse.NAMESPACE,
                    userConfig.region,
                    bucket,
                ).getOrElse { exception ->
                    log.warn { "Failed to create S3 secret: ${exception.message}" }
                    outputHandler.handleMessage("Warning: S3 storage policy may not work (no S3 bucket configured)")
                }
        } else {
            outputHandler.handleMessage("Note: S3 bucket not configured. Only 'local' storage policy available.")
        }

        // Apply all manifests from directory (auto-discovers YAML files, sorted by name)
        log.info { "Applying ClickHouse manifests from $K8S_MANIFEST_BASE" }
        k8sService
            .applyManifests(controlNode, Path.of(K8S_MANIFEST_BASE))
            .getOrElse { exception ->
                error("Failed to apply ClickHouse manifests: ${exception.message}")
            }

        // Scale the ClickHouse StatefulSet to the desired replica count
        k8sService
            .scaleStatefulSet(controlNode, Constants.ClickHouse.NAMESPACE, "clickhouse", actualReplicas)
            .getOrElse { exception ->
                error("Failed to scale ClickHouse StatefulSet: ${exception.message}")
            }

        // Wait for pods to be ready
        if (!skipWait) {
            outputHandler.handleMessage("Waiting for ClickHouse pods to be ready (this may take a few minutes)...")
            k8sService
                .waitForPodsReady(controlNode, timeoutSeconds, Constants.ClickHouse.NAMESPACE)
                .getOrElse { exception ->
                    outputHandler.handleError("Warning: Pods may not be ready: ${exception.message}")
                    outputHandler.handleMessage("You can check status with: easy-db-lab clickhouse status")
                }
        }

        val dbNodeIp = dbHosts.first().privateIp

        // Display access information
        outputHandler.handleMessage("")
        outputHandler.handleMessage("ClickHouse cluster deployed successfully!")
        outputHandler.handleMessage("")
        outputHandler.handleMessage("Storage policies available:")
        outputHandler.handleMessage("  - local: Local disk storage")
        if (!bucket.isNullOrBlank()) {
            outputHandler.handleMessage("  - s3_main: S3 with local cache (bucket: $bucket)")
        }
        outputHandler.handleMessage("")
        outputHandler.handleMessage("Example: CREATE TABLE t (...) SETTINGS storage_policy = 's3_main';")
        outputHandler.handleMessage("")
        outputHandler.handleMessage("HTTP Interface: http://$dbNodeIp:${Constants.ClickHouse.HTTP_PORT}")
        outputHandler.handleMessage("Native Protocol: $dbNodeIp:${Constants.ClickHouse.NATIVE_PORT}")
        outputHandler.handleMessage("")
        outputHandler.handleMessage("Connect with: clickhouse-client --host $dbNodeIp")
    }
}
