package com.rustyrazorblade.easycasslab.commands

import com.github.ajalt.mordant.TermColors
import com.rustyrazorblade.easycasslab.Constants
import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.annotations.McpCommand
import com.rustyrazorblade.easycasslab.annotations.RequireDocker
import com.rustyrazorblade.easycasslab.annotations.RequireProfileSetup
import com.rustyrazorblade.easycasslab.commands.mixins.HostsMixin
import com.rustyrazorblade.easycasslab.configuration.AxonOpsWorkbenchConfig
import com.rustyrazorblade.easycasslab.configuration.ClusterConfigWriter
import com.rustyrazorblade.easycasslab.configuration.ClusterHost
import com.rustyrazorblade.easycasslab.configuration.ClusterState
import com.rustyrazorblade.easycasslab.configuration.EMRClusterState
import com.rustyrazorblade.easycasslab.configuration.InfrastructureState
import com.rustyrazorblade.easycasslab.configuration.ServerType
import com.rustyrazorblade.easycasslab.configuration.User
import com.rustyrazorblade.easycasslab.configuration.getHosts
import com.rustyrazorblade.easycasslab.configuration.toHost
import com.rustyrazorblade.easycasslab.providers.aws.EBSConfig
import com.rustyrazorblade.easycasslab.providers.aws.EC2InstanceService
import com.rustyrazorblade.easycasslab.providers.aws.EC2Service
import com.rustyrazorblade.easycasslab.providers.aws.EMRClusterConfig
import com.rustyrazorblade.easycasslab.providers.aws.EMRService
import com.rustyrazorblade.easycasslab.providers.aws.InstanceCreationConfig
import com.rustyrazorblade.easycasslab.providers.aws.RetryUtil
import com.rustyrazorblade.easycasslab.providers.aws.VpcService
import com.rustyrazorblade.easycasslab.services.HostOperationsService
import com.rustyrazorblade.easycasslab.services.K3sAgentService
import com.rustyrazorblade.easycasslab.services.K3sService
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.resilience4j.retry.Retry
import org.koin.core.component.inject
import picocli.CommandLine.Command
import picocli.CommandLine.Mixin
import picocli.CommandLine.Option
import java.io.File
import java.net.URL
import java.nio.file.Path
import java.time.Duration

/**
 * Starts instances and sets up the cluster.
 */
@McpCommand
@RequireDocker
@RequireProfileSetup
@Command(
    name = "up",
    description = ["Starts instances"],
)
class Up(
    context: Context,
) : PicoBaseCommand(context) {
    private val userConfig: User by inject()
    private val k3sService: K3sService by inject()
    private val k3sAgentService: K3sAgentService by inject()
    private val vpcService: VpcService by inject()
    private val ec2InstanceService: EC2InstanceService by inject()
    private val emrService: EMRService by inject()
    private val ec2Service: EC2Service by inject()
    private val hostOperationsService: HostOperationsService by inject()

    // Working copy loaded during execute() - modified and saved
    private lateinit var workingState: ClusterState

    companion object {
        private val log = KotlinLogging.logger {}
        private val SSH_STARTUP_DELAY = Duration.ofSeconds(5)

        /**
         * Gets the external IP address of the machine running easy-cass-lab.
         * Used to restrict SSH access to the security group.
         */
        private fun getExternalIpAddress(): String = URL("https://api.ipify.org/").readText()
    }

    @Option(names = ["--no-setup", "-n"])
    var noSetup = false

    @Mixin
    var hosts = HostsMixin()

    override fun execute() {
        workingState = clusterStateManager.load()
        val initConfig =
            workingState.initConfig
                ?: error("No init config found. Please run 'easy-cass-lab init' first.")

        provisionInfrastructure(initConfig)
        writeConfigurationFiles()
        WriteConfig(context).execute()
        waitForSshAndDownloadVersions()
        setupInstancesIfNeeded()
    }

    @Suppress("TooGenericExceptionCaught", "LongMethod")
    private fun provisionInfrastructure(initConfig: com.rustyrazorblade.easycasslab.configuration.InitConfig) {
        outputHandler.handleMessage("Provisioning infrastructure...")

        val vpcId = workingState.vpcId ?: error("VPC ID not found. Please run 'easy-cass-lab init' first.")

        // Validate VPC exists in AWS
        val vpcName = vpcService.getVpcName(vpcId)
        if (vpcName == null) {
            error(
                "VPC $vpcId not found in AWS. It may have been deleted. " +
                    "Please run 'easy-cass-lab clean' and 'easy-cass-lab init' to create a new VPC.",
            )
        }
        log.info { "Validated VPC exists: $vpcId ($vpcName)" }

        // Set up VPC infrastructure
        val (subnetIds, securityGroupId, igwId) = setupVpcInfrastructure(vpcId, initConfig)

        // Get AMI ID
        val amiId =
            if (initConfig.ami.isNotBlank()) {
                initConfig.ami
            } else {
                val arch = initConfig.arch.lowercase()
                val amiPattern = Constants.AWS.AMI_PATTERN_TEMPLATE.format(arch)
                val amis = ec2Service.listPrivateAMIs(amiPattern)
                if (amis.isEmpty()) {
                    error("No AMI found for architecture $arch. Please build an AMI first with 'easy-cass-lab build-ami'.")
                }
                // Get the most recently created AMI
                amis.maxByOrNull { it.creationDate }?.id
                    ?: error("No AMI found for architecture $arch")
            }

        // Create EBS config if not NONE
        val ebsConfig =
            if (initConfig.ebsType != "NONE") {
                EBSConfig(
                    volumeType = initConfig.ebsType.lowercase(),
                    volumeSize = initConfig.ebsSize,
                    iops = if (initConfig.ebsIops > 0) initConfig.ebsIops else null,
                    throughput = if (initConfig.ebsThroughput > 0) initConfig.ebsThroughput else null,
                )
            } else {
                null
            }

        val baseTags =
            mapOf(
                "easy_cass_lab" to "1",
                "ClusterId" to workingState.clusterId,
            ) + initConfig.tags

        // Discover existing instances for this cluster
        val existingInstances = ec2InstanceService.findInstancesByClusterId(workingState.clusterId)
        val existingCassandraCount = existingInstances[ServerType.Cassandra]?.size ?: 0
        val existingStressCount = existingInstances[ServerType.Stress]?.size ?: 0
        val existingControlCount = existingInstances[ServerType.Control]?.size ?: 0

        if (existingInstances.values.flatten().isNotEmpty()) {
            outputHandler.handleMessage(
                "Discovered existing instances: " +
                    "Cassandra=$existingCassandraCount, " +
                    "Stress=$existingStressCount, " +
                    "Control=$existingControlCount",
            )
        }

        // Create instances for each server type (only if needed)
        val allHosts = mutableMapOf<ServerType, List<ClusterHost>>()

        // Convert existing instances to ClusterHosts
        existingInstances.forEach { (serverType, instances) ->
            if (instances.isNotEmpty()) {
                allHosts[serverType] = instances.map { it.toClusterHost() }
            }
        }

        // Cassandra instances - create only what's needed
        val neededCassandraCount = initConfig.cassandraInstances - existingCassandraCount
        if (neededCassandraCount > 0) {
            val cassandraHosts =
                createInstancesForType(
                    serverType = ServerType.Cassandra,
                    count = neededCassandraCount,
                    instanceType = initConfig.instanceType,
                    amiId = amiId,
                    securityGroupId = securityGroupId,
                    subnetIds = subnetIds,
                    ebsConfig = ebsConfig,
                    tags = baseTags,
                    clusterName = initConfig.name,
                    startIndex = existingCassandraCount,
                )
            allHosts[ServerType.Cassandra] = (allHosts[ServerType.Cassandra] ?: emptyList()) + cassandraHosts
        } else if (initConfig.cassandraInstances > 0 && existingCassandraCount > 0) {
            outputHandler.handleMessage("Found $existingCassandraCount existing Cassandra instances, no new instances needed")
        }

        // Stress instances - create only what's needed
        val neededStressCount = initConfig.stressInstances - existingStressCount
        if (neededStressCount > 0) {
            val stressHosts =
                createInstancesForType(
                    serverType = ServerType.Stress,
                    count = neededStressCount,
                    instanceType = initConfig.stressInstanceType,
                    amiId = amiId,
                    securityGroupId = securityGroupId,
                    subnetIds = subnetIds,
                    ebsConfig = null, // stress instances don't need EBS
                    tags = baseTags,
                    clusterName = initConfig.name,
                    startIndex = existingStressCount,
                )
            allHosts[ServerType.Stress] = (allHosts[ServerType.Stress] ?: emptyList()) + stressHosts
        } else if (initConfig.stressInstances > 0 && existingStressCount > 0) {
            outputHandler.handleMessage("Found $existingStressCount existing Stress instances, no new instances needed")
        }

        // Control instances - create only what's needed
        val neededControlCount = initConfig.controlInstances - existingControlCount
        if (neededControlCount > 0) {
            val controlHosts =
                createInstancesForType(
                    serverType = ServerType.Control,
                    count = neededControlCount,
                    instanceType = initConfig.controlInstanceType,
                    amiId = amiId,
                    securityGroupId = securityGroupId,
                    subnetIds = subnetIds,
                    ebsConfig = null, // control instances don't need EBS
                    tags = baseTags,
                    clusterName = initConfig.name,
                    startIndex = existingControlCount,
                )
            allHosts[ServerType.Control] = (allHosts[ServerType.Control] ?: emptyList()) + controlHosts
        } else if (initConfig.controlInstances > 0 && existingControlCount > 0) {
            outputHandler.handleMessage("Found $existingControlCount existing Control instances, no new instances needed")
        }

        // Update cluster state with hosts and infrastructure
        workingState.updateHosts(allHosts)
        workingState.updateInfrastructure(
            InfrastructureState(
                vpcId = vpcId,
                subnetIds = subnetIds,
                securityGroupId = securityGroupId,
                internetGatewayId = igwId,
            ),
        )
        workingState.markInfrastructureUp()

        // Create EMR cluster if enabled
        if (initConfig.sparkEnabled) {
            createEmrCluster(initConfig, subnetIds.first(), baseTags)
        }

        clusterStateManager.save(workingState)

        with(TermColors()) {
            outputHandler.handleMessage(
                "Instances have been provisioned.\n\n" +
                    "Use " + green("easy-cass-lab list") + " to see all available versions\n\n" +
                    "Then use " + green("easy-cass-lab use <version>") +
                    " to use a specific version of Cassandra.\n",
            )
            outputHandler.handleMessage("Writing ssh config file to sshConfig.")
            outputHandler.handleMessage(
                "The following alias will allow you to easily work with the cluster:\n\n" +
                    green("source env.sh") + "\n",
            )
            outputHandler.handleMessage(
                "You can edit " + green("cassandra.patch.yaml") +
                    " with any changes you'd like to see merge in into the remote cassandra.yaml file.",
            )
        }

        outputHandler.handleMessage("Cluster state updated: ${allHosts.values.flatten().size} hosts tracked")
    }

    private fun setupVpcInfrastructure(
        vpcId: String,
        initConfig: com.rustyrazorblade.easycasslab.configuration.InitConfig,
    ): Triple<List<String>, String, String> {
        val baseTags = mapOf("easy_cass_lab" to "1", "ClusterId" to workingState.clusterId)

        // Determine availability zones
        val azs =
            if (initConfig.azs.isNotEmpty()) {
                initConfig.azs.map { initConfig.region + it }
            } else {
                listOf(initConfig.region + "a", initConfig.region + "b", initConfig.region + "c")
            }

        // Create subnets in each AZ
        val subnetIds =
            azs.mapIndexed { index, az ->
                vpcService.findOrCreateSubnet(
                    vpcId = vpcId,
                    name = "${initConfig.name}-subnet-$index",
                    cidr = Constants.Vpc.subnetCidr(index),
                    tags = baseTags,
                    availabilityZone = az,
                )
            }

        // Create internet gateway
        val igwId =
            vpcService.findOrCreateInternetGateway(
                vpcId = vpcId,
                name = "${initConfig.name}-igw",
                tags = baseTags,
            )

        // Ensure route tables are configured
        subnetIds.forEach { subnetId ->
            vpcService.ensureRouteTable(vpcId, subnetId, igwId)
        }

        // Create security group
        val securityGroupId =
            vpcService.findOrCreateSecurityGroup(
                vpcId = vpcId,
                name = "${initConfig.name}-sg",
                description = "Security group for easy-cass-lab cluster ${initConfig.name}",
                tags = baseTags,
            )

        // Configure security group rules
        val sshCidr = if (initConfig.open) "0.0.0.0/0" else "${getExternalIpAddress()}/32"
        vpcService.authorizeSecurityGroupIngress(securityGroupId, 22, 22, sshCidr) // SSH

        // Allow all traffic within the VPC (for Cassandra communication) - both TCP and UDP
        vpcService.authorizeSecurityGroupIngress(securityGroupId, 0, 65535, Constants.Vpc.DEFAULT_CIDR, "tcp")
        vpcService.authorizeSecurityGroupIngress(securityGroupId, 0, 65535, Constants.Vpc.DEFAULT_CIDR, "udp")

        return Triple(subnetIds, securityGroupId, igwId)
    }

    private fun createInstancesForType(
        serverType: ServerType,
        count: Int,
        instanceType: String,
        amiId: String,
        securityGroupId: String,
        subnetIds: List<String>,
        ebsConfig: EBSConfig?,
        tags: Map<String, String>,
        clusterName: String,
        startIndex: Int = 0,
    ): List<ClusterHost> {
        val config =
            InstanceCreationConfig(
                serverType = serverType,
                count = count,
                instanceType = instanceType,
                amiId = amiId,
                keyName = userConfig.keyName,
                securityGroupId = securityGroupId,
                subnetIds = subnetIds,
                iamInstanceProfile = Constants.AWS.Roles.EC2_INSTANCE_ROLE,
                ebsConfig = ebsConfig,
                tags = tags,
                clusterName = clusterName,
                startIndex = startIndex,
            )

        val createdInstances = ec2InstanceService.createInstances(config)

        // Wait for instances to be running
        val instanceIds = createdInstances.map { it.instanceId }
        ec2InstanceService.waitForInstancesRunning(instanceIds)

        // Update with final IPs
        val updatedInstances = ec2InstanceService.updateInstanceIps(createdInstances)

        return updatedInstances.map { instance ->
            ClusterHost(
                publicIp = instance.publicIp,
                privateIp = instance.privateIp,
                alias = instance.alias,
                availabilityZone = instance.availabilityZone,
                instanceId = instance.instanceId,
            )
        }
    }

    private fun createEmrCluster(
        initConfig: com.rustyrazorblade.easycasslab.configuration.InitConfig,
        subnetId: String,
        tags: Map<String, String>,
    ) {
        outputHandler.handleMessage("Creating EMR Spark cluster...")

        val emrConfig =
            EMRClusterConfig(
                clusterName = "${initConfig.name}-spark",
                logUri = "s3://${userConfig.s3Bucket}/emr-logs/${workingState.clusterId}/",
                subnetId = subnetId,
                ec2KeyName = userConfig.keyName,
                masterInstanceType = initConfig.sparkMasterInstanceType,
                coreInstanceType = initConfig.sparkWorkerInstanceType,
                coreInstanceCount = initConfig.sparkWorkerCount,
                tags = tags,
            )

        val result = emrService.createCluster(emrConfig)
        val readyResult = emrService.waitForClusterReady(result.clusterId)

        workingState.updateEmrCluster(
            EMRClusterState(
                clusterId = readyResult.clusterId,
                clusterName = readyResult.clusterName,
                masterPublicDns = readyResult.masterPublicDns,
                state = readyResult.state,
            ),
        )

        outputHandler.handleMessage("EMR cluster ready: ${readyResult.masterPublicDns}")
    }

    private fun writeConfigurationFiles() {
        val config = File("sshConfig").bufferedWriter()
        ClusterConfigWriter.writeSshConfig(config, userConfig.sshKeyPath, workingState.hosts)
        val envFile = File("env.sh").bufferedWriter()
        ClusterConfigWriter.writeEnvironmentFile(envFile, workingState.hosts, userConfig.sshKeyPath)
        writeStressEnvironmentVariables()
        writeAxonOpsWorkbenchConfig()
    }

    @Suppress("TooGenericExceptionCaught")
    private fun writeAxonOpsWorkbenchConfig() {
        try {
            val cassandraHosts = workingState.getHosts(ServerType.Cassandra)
            if (cassandraHosts.isNotEmpty()) {
                val cassandra0 = cassandraHosts.first()
                val config =
                    AxonOpsWorkbenchConfig.create(
                        host = cassandra0,
                        userConfig = userConfig,
                        clusterName = "easy-cass-lab",
                    )
                val configFile = File("axonops-workbench.json")
                AxonOpsWorkbenchConfig.writeToFile(config, configFile)
                outputHandler.handleMessage(
                    "AxonOps Workbench configuration written to axonops-workbench.json",
                )
            } else {
                log.warn { "No Cassandra hosts found, skipping AxonOps Workbench configuration" }
            }
        } catch (e: Exception) {
            log.error(e) { "Failed to write AxonOps Workbench configuration" }
        }
    }

    private fun writeStressEnvironmentVariables() {
        val cassandraHosts = workingState.getHosts(ServerType.Cassandra)
        if (cassandraHosts.isEmpty()) {
            log.warn { "No Cassandra hosts found, skipping stress environment variables" }
            return
        }
        val cassandraHost = cassandraHosts.first().private
        val datacenter = workingState.initConfig?.region ?: userConfig.region

        val stressEnvironmentVars = File("environment.sh").bufferedWriter()
        stressEnvironmentVars.write("#!/usr/bin/env bash")
        stressEnvironmentVars.newLine()
        stressEnvironmentVars.write("export CASSANDRA_EASY_STRESS_CASSANDRA_HOST=$cassandraHost")
        stressEnvironmentVars.newLine()
        stressEnvironmentVars.write("export CASSANDRA_EASY_STRESS_PROM_PORT=0")
        stressEnvironmentVars.newLine()
        stressEnvironmentVars.write("export CASSANDRA_EASY_STRESS_DEFAULT_DC=$datacenter")
        stressEnvironmentVars.newLine()
        stressEnvironmentVars.flush()
        stressEnvironmentVars.close()
    }

    private fun waitForSshAndDownloadVersions() {
        outputHandler.handleMessage("Waiting for SSH to come up..")
        Thread.sleep(SSH_STARTUP_DELAY.toMillis())

        val retryConfig = RetryUtil.createSshConnectionRetryConfig()
        val retry =
            Retry.of("ssh-connection", retryConfig).also {
                it.eventPublisher.onRetry { event ->
                    outputHandler.handleMessage(
                        "SSH still not up yet, waiting... (attempt ${event.numberOfRetryAttempts})",
                    )
                }
            }

        Retry
            .decorateRunnable(retry) {
                hostOperationsService.withHosts(
                    workingState.hosts,
                    ServerType.Cassandra,
                    hosts.hostList,
                ) { clusterHost ->
                    val host = clusterHost.toHost()
                    remoteOps.executeRemotely(host, "echo 1").text
                    if (!File("cassandra_versions.yaml").exists()) {
                        remoteOps.download(
                            host,
                            "/etc/cassandra_versions.yaml",
                            Path.of("cassandra_versions.yaml"),
                        )
                    }
                }
            }.run()
    }

    private fun setupInstancesIfNeeded() {
        if (noSetup) {
            with(TermColors()) {
                outputHandler.handleMessage(
                    "Skipping node setup.  You will need to run " +
                        green("easy-cass-lab setup-instance") + " to complete setup",
                )
            }
        } else {
            SetupInstance(context).execute()
            startK3sOnAllNodes()

            if (userConfig.axonOpsKey.isNotBlank() && userConfig.axonOpsOrg.isNotBlank()) {
                outputHandler.handleMessage("Setting up axonops for ${userConfig.axonOpsOrg}")
                ConfigureAxonOps(context).execute()
            }
        }
    }

    @Suppress("ReturnCount")
    private fun startK3sOnAllNodes() {
        outputHandler.handleMessage("Starting K3s cluster...")

        val controlHosts = workingState.getHosts(ServerType.Control)
        if (controlHosts.isEmpty()) {
            outputHandler.handleError("No control nodes found, skipping K3s setup")
            return
        }

        val controlNode = controlHosts.first()
        outputHandler.handleMessage("Starting K3s server on control node ${controlNode.alias}...")

        k3sService
            .start(controlNode)
            .onFailure { error ->
                log.error(error) { "Failed to start K3s server on ${controlNode.alias}" }
                outputHandler.handleError("Failed to start K3s server: ${error.message}")
                return
            }.onSuccess {
                log.info { "Successfully started K3s server on ${controlNode.alias}" }
            }

        val nodeToken =
            k3sService
                .getNodeToken(controlNode)
                .onFailure { error ->
                    log.error(error) { "Failed to retrieve K3s node token from ${controlNode.alias}" }
                    outputHandler.handleError("Failed to retrieve K3s node token: ${error.message}")
                    return
                }.getOrThrow()

        log.info { "Retrieved K3s node token from ${controlNode.alias}" }

        k3sService
            .downloadAndConfigureKubeconfig(controlNode, File("kubeconfig").toPath())
            .onFailure { error ->
                log.error(error) { "Failed to download kubeconfig from ${controlNode.alias}" }
                outputHandler.handleError("Failed to download kubeconfig: ${error.message}")
            }.onSuccess {
                outputHandler.handleMessage("Kubeconfig written to kubeconfig")
                outputHandler.handleMessage("Use 'source env.sh' to configure kubectl for cluster access")
            }

        val serverUrl = "https://${controlNode.private}:6443"
        val workerServerTypes = listOf(ServerType.Cassandra, ServerType.Stress)

        workerServerTypes.forEach { serverType ->
            val nodeLabels =
                when (serverType) {
                    ServerType.Cassandra -> mapOf("role" to "cassandra", "type" to "db")
                    ServerType.Stress -> mapOf("role" to "stress", "type" to "app")
                    ServerType.Control -> emptyMap()
                }

            hostOperationsService.withHosts(
                workingState.hosts,
                serverType,
                hosts.hostList,
                parallel = true,
            ) { clusterHost ->
                val host = clusterHost.toHost()
                outputHandler.handleMessage("Configuring K3s agent on ${host.alias} with labels: $nodeLabels...")

                k3sAgentService
                    .configure(host, serverUrl, nodeToken, nodeLabels)
                    .onFailure { error ->
                        log.error(error) { "Failed to configure K3s agent on ${host.alias}" }
                        outputHandler.handleError("Failed to configure K3s agent on ${host.alias}: ${error.message}")
                        return@withHosts
                    }

                k3sAgentService
                    .start(host)
                    .onFailure { error ->
                        log.error(error) { "Failed to start K3s agent on ${host.alias}" }
                        outputHandler.handleError("Failed to start K3s agent on ${host.alias}: ${error.message}")
                        return@withHosts
                    }.onSuccess {
                        log.info { "Successfully started K3s agent on ${host.alias}" }
                    }
            }
        }

        outputHandler.handleMessage("K3s cluster started successfully")
    }
}
