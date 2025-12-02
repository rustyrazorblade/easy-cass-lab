package com.rustyrazorblade.easydblab.commands

import com.rustyrazorblade.easydblab.Context
import com.rustyrazorblade.easydblab.annotations.McpCommand
import com.rustyrazorblade.easydblab.annotations.RequireProfileSetup
import com.rustyrazorblade.easydblab.configuration.ClusterHost
import com.rustyrazorblade.easydblab.configuration.ClusterStateManager
import com.rustyrazorblade.easydblab.configuration.Host
import com.rustyrazorblade.easydblab.configuration.ServerType
import com.rustyrazorblade.easydblab.configuration.User
import com.rustyrazorblade.easydblab.configuration.s3Path
import com.rustyrazorblade.easydblab.kubernetes.getLocalKubeconfigPath
import com.rustyrazorblade.easydblab.output.OutputHandler
import com.rustyrazorblade.easydblab.providers.aws.EC2InstanceService
import com.rustyrazorblade.easydblab.providers.aws.EMRService
import com.rustyrazorblade.easydblab.providers.aws.SecurityGroupRuleInfo
import com.rustyrazorblade.easydblab.providers.aws.SecurityGroupService
import com.rustyrazorblade.easydblab.providers.ssh.RemoteOperationsService
import com.rustyrazorblade.easydblab.services.K3sService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import picocli.CommandLine.Command
import java.io.File
import java.nio.file.Paths
import java.time.Duration
import java.time.format.DateTimeFormatter

/**
 * Displays comprehensive, human-readable environment status including:
 * - Nodes (cassandra, stress, control) with instance IDs, IPs, aliases, and live state
 * - Networking info (VPC, IGW, subnets, route tables)
 * - Security group rules (full ingress/egress)
 * - Kubernetes jobs running on K3s cluster
 * - Cassandra version (live via SSH, fallback to cached)
 */
@McpCommand
@RequireProfileSetup
@Command(
    name = "status",
    description = ["Display full environment status"],
)
@Suppress("TooManyFunctions")
class Status(
    private val context: Context,
) : PicoCommand,
    KoinComponent {
    companion object {
        private val log = KotlinLogging.logger {}
        private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        private const val JOB_NAME_MAX_LENGTH = 30
        private const val HOURS_PER_DAY = 24
    }

    private val outputHandler: OutputHandler by inject()
    private val clusterStateManager: ClusterStateManager by inject()
    private val ec2InstanceService: EC2InstanceService by inject()
    private val securityGroupService: SecurityGroupService by inject()
    private val k3sService: K3sService by inject()
    private val remoteOperationsService: RemoteOperationsService by inject()
    private val emrService: EMRService by inject()
    private val userConfig: User by inject()

    private val clusterState by lazy { clusterStateManager.load() }

    override fun execute() {
        if (!clusterStateManager.exists()) {
            outputHandler.handleMessage(
                "Cluster state does not exist yet. Run 'easy-db-lab init' first.",
            )
            return
        }

        displayClusterSection()
        displayNodesSection()
        displayNetworkingSection()
        displaySecurityGroupSection()
        displaySparkClusterSection()
        displayS3BucketSection()
        displayKubernetesSection()
        displayCassandraVersionSection()
    }

    /**
     * Display cluster overview section
     */
    private fun displayClusterSection() {
        outputHandler.handleMessage("")
        outputHandler.handleMessage("=== CLUSTER STATUS ===")
        outputHandler.handleMessage("Cluster ID: ${clusterState.clusterId}")
        outputHandler.handleMessage("Name: ${clusterState.name}")
        outputHandler.handleMessage("Created: ${clusterState.createdAt.atZone(java.time.ZoneId.systemDefault()).format(DATE_FORMATTER)}")
        outputHandler.handleMessage("Infrastructure: ${clusterState.infrastructureStatus}")
    }

    /**
     * Display nodes section with live instance state from EC2
     */
    private fun displayNodesSection() {
        outputHandler.handleMessage("")
        outputHandler.handleMessage("=== NODES ===")

        val allInstanceIds = clusterState.getAllInstanceIds()

        // Get live instance states from EC2
        val instanceStates =
            if (allInstanceIds.isNotEmpty()) {
                runCatching {
                    ec2InstanceService
                        .describeInstances(allInstanceIds)
                        .associateBy { it.instanceId }
                }.getOrElse {
                    log.warn(it) { "Failed to get instance states from EC2" }
                    emptyMap()
                }
            } else {
                emptyMap()
            }

        displayNodesByType(ServerType.Cassandra, "CASSANDRA NODES", instanceStates)
        displayNodesByType(ServerType.Stress, "STRESS NODES", instanceStates)
        displayNodesByType(ServerType.Control, "CONTROL NODES", instanceStates)
    }

    private fun displayNodesByType(
        serverType: ServerType,
        header: String,
        instanceStates: Map<String, com.rustyrazorblade.easydblab.providers.aws.InstanceDetails>,
    ) {
        val hosts = clusterState.hosts[serverType] ?: emptyList()
        if (hosts.isEmpty()) return

        outputHandler.handleMessage("")
        outputHandler.handleMessage("$header:")

        hosts.forEach { host ->
            val state = instanceStates[host.instanceId]?.state ?: "UNKNOWN"
            outputHandler.handleMessage(
                "  %-12s %-20s %-16s %-16s %-12s %s".format(
                    host.alias,
                    host.instanceId.ifEmpty { "(no id)" },
                    "${host.publicIp} (public)",
                    "${host.privateIp} (private)",
                    host.availabilityZone,
                    state.uppercase(),
                ),
            )
        }
    }

    /**
     * Display networking section
     */
    private fun displayNetworkingSection() {
        outputHandler.handleMessage("")
        outputHandler.handleMessage("=== NETWORKING ===")

        val infrastructure = clusterState.infrastructure
        if (infrastructure == null) {
            outputHandler.handleMessage("(no infrastructure data)")
            return
        }

        outputHandler.handleMessage("VPC:              ${infrastructure.vpcId}")
        outputHandler.handleMessage("Internet Gateway: ${infrastructure.internetGatewayId ?: "(none)"}")
        outputHandler.handleMessage("Subnets:          ${infrastructure.subnetIds.joinToString(", ")}")
        outputHandler.handleMessage("Route Tables:     ${infrastructure.routeTableId ?: "(default)"}")
    }

    /**
     * Display security group rules section
     */
    private fun displaySecurityGroupSection() {
        outputHandler.handleMessage("")
        outputHandler.handleMessage("=== SECURITY GROUP ===")

        val sgId = clusterState.infrastructure?.securityGroupId
        if (sgId == null) {
            outputHandler.handleMessage("(no security group configured)")
            return
        }

        val sgDetails =
            runCatching {
                securityGroupService.describeSecurityGroup(sgId)
            }.getOrNull()

        if (sgDetails == null) {
            outputHandler.handleMessage("Security Group: $sgId (unable to fetch details)")
            return
        }

        outputHandler.handleMessage("Security Group: ${sgDetails.securityGroupId} (${sgDetails.name})")

        outputHandler.handleMessage("")
        outputHandler.handleMessage("Inbound Rules:")
        displaySecurityRules(sgDetails.inboundRules)

        outputHandler.handleMessage("")
        outputHandler.handleMessage("Outbound Rules:")
        displaySecurityRules(sgDetails.outboundRules)
    }

    private fun displaySecurityRules(rules: List<SecurityGroupRuleInfo>) {
        if (rules.isEmpty()) {
            outputHandler.handleMessage("  (none)")
            return
        }

        rules.forEach { rule ->
            val portRange =
                when {
                    rule.fromPort == null && rule.toPort == null -> "All"
                    rule.fromPort == rule.toPort -> "${rule.fromPort}"
                    else -> "${rule.fromPort}-${rule.toPort}"
                }

            val cidrs = rule.cidrBlocks.joinToString(", ").ifEmpty { "(none)" }
            val description = rule.description ?: ""

            outputHandler.handleMessage(
                "  %-6s %-8s %-20s %s".format(
                    rule.protocol,
                    portRange,
                    cidrs,
                    description,
                ),
            )
        }
    }

    /**
     * Display Spark/EMR cluster section
     */
    private fun displaySparkClusterSection() {
        outputHandler.handleMessage("")
        outputHandler.handleMessage("=== SPARK CLUSTER ===")

        val emrCluster = clusterState.emrCluster
        if (emrCluster == null) {
            outputHandler.handleMessage("(no Spark cluster configured)")
            return
        }

        // Try to get live status from AWS, fall back to cached state
        val liveState =
            runCatching {
                emrService.getClusterStatus(emrCluster.clusterId).state
            }.getOrElse { emrCluster.state }

        outputHandler.handleMessage("Cluster ID:   ${emrCluster.clusterId}")
        outputHandler.handleMessage("Name:         ${emrCluster.clusterName}")
        outputHandler.handleMessage("State:        $liveState")
        emrCluster.masterPublicDns?.let {
            outputHandler.handleMessage("Master DNS:   $it")
        }
    }

    /**
     * Display S3 bucket section
     */
    private fun displayS3BucketSection() {
        outputHandler.handleMessage("")
        outputHandler.handleMessage("=== S3 BUCKET ===")

        if (userConfig.s3Bucket.isBlank()) {
            outputHandler.handleMessage("(no S3 bucket configured)")
            return
        }

        val s3Path = clusterState.s3Path(userConfig)
        outputHandler.handleMessage("Bucket:       ${userConfig.s3Bucket}")
        outputHandler.handleMessage("Spark JARs:   ${s3Path.sparkJars()}")
        outputHandler.handleMessage("Logs:         ${s3Path.logs()}")
        outputHandler.handleMessage("EMR Logs:     ${s3Path.emrLogs()}")
    }

    /**
     * Display Kubernetes jobs section
     */
    private fun displayKubernetesSection() {
        outputHandler.handleMessage("")
        outputHandler.handleMessage("=== KUBERNETES JOBS ===")

        val controlHost = clusterState.getControlHost()
        if (controlHost == null) {
            outputHandler.handleMessage("(no control node configured)")
            return
        }

        // Check if kubeconfig exists locally
        val kubeconfigPath = getLocalKubeconfigPath(context.workingDirectory.absolutePath)
        if (!File(kubeconfigPath).exists()) {
            outputHandler.handleMessage("(kubeconfig not found - K3s may not be initialized)")
            return
        }

        // Try to connect and list jobs via K3sService
        val result = k3sService.listJobs(controlHost, Paths.get(kubeconfigPath))

        result.fold(
            onSuccess = { jobs ->
                if (jobs.isEmpty()) {
                    outputHandler.handleMessage("(no jobs running)")
                } else {
                    outputHandler.handleMessage("%-12s %-30s %-12s %s".format("NAMESPACE", "NAME", "STATUS", "AGE"))
                    jobs.forEach { job ->
                        outputHandler.handleMessage(
                            "%-12s %-30s %-12s %s".format(
                                job.namespace,
                                job.name.take(JOB_NAME_MAX_LENGTH),
                                job.status,
                                formatAge(job.age),
                            ),
                        )
                    }
                }
            },
            onFailure = { e ->
                log.debug(e) { "Failed to get Kubernetes jobs" }
                outputHandler.handleMessage("(unable to connect to K3s: ${e.message})")
            },
        )
    }

    /**
     * Display Cassandra version section
     */
    private fun displayCassandraVersionSection() {
        outputHandler.handleMessage("")
        outputHandler.handleMessage("=== CASSANDRA VERSION ===")

        val cassandraHosts = clusterState.hosts[ServerType.Cassandra] ?: emptyList()
        if (cassandraHosts.isEmpty()) {
            outputHandler.handleMessage("(no Cassandra nodes configured)")
            return
        }

        // Try to get live version from first available node
        val liveVersion = tryGetLiveVersion(cassandraHosts)

        if (liveVersion != null) {
            outputHandler.handleMessage("Version: $liveVersion (all nodes)")
        } else {
            // Fall back to cached version
            val cachedVersion = clusterState.default.version.ifEmpty { "unknown" }
            outputHandler.handleMessage("Version: $cachedVersion (cached - nodes unavailable)")
        }
    }

    private fun tryGetLiveVersion(hosts: List<ClusterHost>): String? {
        for (host in hosts) {
            val version =
                runCatching {
                    val sshHost = host.toHost()
                    val result = remoteOperationsService.getRemoteVersion(sshHost)
                    result.versionString
                }.onFailure { e ->
                    log.debug(e) { "Failed to get version from ${host.alias}" }
                }.getOrNull()

            if (!version.isNullOrEmpty()) {
                return version
            }
        }
        return null
    }

    private fun formatAge(duration: Duration): String {
        val hours = duration.toHours()
        val minutes = duration.toMinutesPart()

        return when {
            hours > HOURS_PER_DAY -> "${hours / HOURS_PER_DAY}d"
            hours > 0 -> "${hours}h"
            else -> "${minutes}m"
        }
    }
}

/**
 * Extension function to convert ClusterHost to Host for SSH operations
 */
private fun ClusterHost.toHost(): Host =
    Host(
        public = this.publicIp,
        private = this.privateIp,
        alias = this.alias,
        availabilityZone = this.availabilityZone,
    )
