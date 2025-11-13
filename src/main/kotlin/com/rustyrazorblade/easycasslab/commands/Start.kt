package com.rustyrazorblade.easycasslab.commands

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import com.beust.jcommander.ParametersDelegate
import com.github.ajalt.mordant.TermColors
import com.rustyrazorblade.easycasslab.Constants
import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.annotations.McpCommand
import com.rustyrazorblade.easycasslab.annotations.PostExecute
import com.rustyrazorblade.easycasslab.annotations.RequireDocker
import com.rustyrazorblade.easycasslab.annotations.RequireSSHKey
import com.rustyrazorblade.easycasslab.commands.delegates.Hosts
import com.rustyrazorblade.easycasslab.configuration.ClusterState
import com.rustyrazorblade.easycasslab.configuration.ServerType
import com.rustyrazorblade.easycasslab.configuration.User
import com.rustyrazorblade.easycasslab.services.CassandraService
import com.rustyrazorblade.easycasslab.services.EasyStressService
import com.rustyrazorblade.easycasslab.services.SidecarService
import com.rustyrazorblade.easycasslab.ssh.tunnel.SSHTunnel
import com.rustyrazorblade.easycasslab.ssh.tunnel.SSHTunnelManager
import org.koin.core.component.inject
import java.io.File

@McpCommand
@RequireDocker
@RequireSSHKey
@Parameters(commandDescription = "Start cassandra on all nodes via service command")
class Start(
    context: Context,
) : BaseCommand(context) {
    private val userConfig: User by inject()
    private val cassandraService: CassandraService by inject()
    private val easyStressService: EasyStressService by inject()
    private val sidecarService: SidecarService by inject()

    companion object {
        private const val DEFAULT_SLEEP_BETWEEN_STARTS_SECONDS = 120L
        private const val DOCKER_COMPOSE_STARTUP_DELAY_MS = 5000L
    }

    @Parameter(names = ["--sleep"], description = "Time to sleep between starts in seconds")
    var sleep: Long = DEFAULT_SLEEP_BETWEEN_STARTS_SECONDS

    @ParametersDelegate var hosts = Hosts()

    override fun execute() {
        with(TermColors()) {
            tfstate.withHosts(ServerType.Cassandra, hosts) {
                outputHandler.handleMessage(green("Starting $it"))
                // start() defaults to wait=true, which includes waiting for UP/NORMAL
                cassandraService.start(it).getOrThrow()
            }

            // Start cassandra-sidecar on Cassandra nodes
            tfstate.withHosts(ServerType.Cassandra, hosts, parallel = true) { host ->
                sidecarService
                    .start(host)
                    .onFailure { e ->
                        outputHandler.handleMessage("Warning: Failed to start cassandra-sidecar on ${host.alias}: ${e.message}")
                    }
            }
        }

        // Start cassandra-easy-stress on stress nodes
        startCassandraEasyStress()

        if (userConfig.axonOpsOrg.isNotBlank() && userConfig.axonOpsKey.isNotBlank()) {
            StartAxonOps(context).execute()
        }

        // Inform user about AxonOps Workbench configuration if it exists
        val axonOpsWorkbenchFile = File("axonops-workbench.json")
        if (axonOpsWorkbenchFile.exists()) {
            outputHandler.handleMessage("")
            outputHandler.handleMessage("AxonOps Workbench configuration available:")
            outputHandler.handleMessage("To import into AxonOps Workbench, run:")
            outputHandler.handleMessage(
                "  /path/to/axonops-workbench -v --import-workspace=axonops-workbench.json",
            )
            outputHandler.handleMessage("")
        }

        // Start OTel collectors on Cassandra nodes
        startOtelOnCassandraNodes()

        // Start OTel collectors on stress nodes
        startOtelOnStressNodes()

        // this has to happen last, because we want Cassandra to be available before starting the
        // MCP server.
        deployDockerComposeToControlNodes()
    }

    /**
     * Start cassandra-easy-stress service on stress nodes
     */
    private fun startCassandraEasyStress() {
        outputHandler.handleMessage("Starting cassandra-easy-stress on stress nodes...")

        tfstate.withHosts(ServerType.Stress, hosts, parallel = true) { host ->
            easyStressService
                .start(host)
                .onFailure { e ->
                    outputHandler.handleMessage("Warning: Failed to start cassandra-easy-stress on ${host.alias}: ${e.message}")
                }
        }

        outputHandler.handleMessage("cassandra-easy-stress startup completed on stress nodes")
    }

    private fun deployDockerComposeToControlNodes() {
        outputHandler.handleMessage("Starting Docker Compose services on control nodes...")

        val dockerComposeFile = File(Constants.ConfigPaths.CONTROL_DOCKER_COMPOSE)
        val otelConfigFile = File(Constants.ConfigPaths.CONTROL_OTEL_CONFIG)

        if (!dockerComposeFile.exists()) {
            throw RuntimeException("control/docker-compose.yaml not found - required file missing")
        }

        if (!otelConfigFile.exists()) {
            throw RuntimeException(
                "control/otel-collector-config.yaml not found - required file missing",
            )
        }

        // Load cluster state to get InitConfig
        val clusterState =
            try {
                ClusterState.load()
            } catch (e: Exception) {
                outputHandler.handleMessage(
                    "Warning: Could not load cluster state, using defaults: ${e.message}",
                )
                null
            }

        // Get datacenter (region) and cassandra host
        val datacenter = clusterState?.initConfig?.region ?: userConfig.region
        val cassandraHost =
            tfstate.getHosts(ServerType.Cassandra).firstOrNull()?.private ?: "cassandra0"

        // Create .env file content
        val envContent =
            """
            CASSANDRA_DATACENTER=$datacenter
            CASSANDRA_HOST=$cassandraHost
            """.trimIndent()

        tfstate.withHosts(ServerType.Control, hosts, parallel = true) { host ->
            outputHandler.handleMessage(
                "Starting Docker Compose services on control node ${host.public}",
            )

            // Create and upload .env file
            outputHandler.handleMessage(
                "Creating .env file with datacenter=$datacenter and host=$cassandraHost",
            )
            val envFile = File.createTempFile("docker", ".env")
            envFile.writeText(envContent)
            remoteOps.upload(host, envFile.toPath(), Constants.Paths.REMOTE_ENV_FILE)
            envFile.delete()

            // Upload required configuration files
            outputHandler.handleMessage("Uploading configuration files to ${host.public}...")
            remoteOps.upload(host, dockerComposeFile.toPath(), Constants.Paths.REMOTE_DOCKER_COMPOSE)
            remoteOps.upload(
                host,
                otelConfigFile.toPath(),
                "${Constants.Paths.REMOTE_HOME}/otel-collector-config.yaml",
            )

            // Check if docker and docker compose are available
            try {
                val dockerCheck =
                    remoteOps.executeRemotely(host, Constants.Docker.Commands.VERSION_CHECK)
                outputHandler.handleMessage("Docker check: ${dockerCheck.text}")
            } catch (e: RuntimeException) {
                outputHandler.handleMessage(
                    "Warning: Docker may not be installed on ${host.public}",
                )
                outputHandler.handleMessage("Error: ${e.message}")
            }

            // Pull Docker images before starting
            outputHandler.handleMessage("Pulling Docker images on ${host.public}...")
            try {
                val pullResult =
                    remoteOps.executeRemotely(
                        host,
                        "cd ${Constants.Paths.REMOTE_HOME} && ${Constants.Docker.Commands.COMPOSE_PULL}",
                    )
                outputHandler.handleMessage("Docker pull output: ${pullResult.text}")
            } catch (e: RuntimeException) {
                outputHandler.handleMessage("Warning: Failed to pull Docker images: ${e.message}")
                outputHandler.handleMessage(
                    "Will attempt to start anyway (images may be pulled automatically)...",
                )
            }

            // Run docker compose up in detached mode (retry handled by RemoteOperationsService)
            try {
                val result =
                    remoteOps.executeRemotely(
                        host,
                        "cd ${Constants.Paths.REMOTE_HOME} && ${Constants.Docker.Commands.COMPOSE_UP_DETACHED}",
                    )
                outputHandler.handleMessage("Docker Compose output: ${result.text}")

                // Wait for services to be ready
                outputHandler.handleMessage("Waiting for services to start...")
                Thread.sleep(DOCKER_COMPOSE_STARTUP_DELAY_MS) // Give services time to start

                // Check service status
                try {
                    val statusResult =
                        remoteOps.executeRemotely(
                            host,
                            "cd ${Constants.Paths.REMOTE_HOME} && ${Constants.Docker.Commands.COMPOSE_PS}",
                        )
                    outputHandler.handleMessage("Service status: ${statusResult.text}")
                } catch (e: Exception) {
                    outputHandler.handleMessage(
                        "Warning: Could not check service status: ${e.message}",
                    )
                }
            } catch (e: Exception) {
                outputHandler.handleMessage(
                    "ERROR: Failed to start Docker Compose: ${e.message}",
                )
                outputHandler.handleMessage(
                    "You may need to manually run: ssh ${host.public} 'cd ${Constants.Paths.REMOTE_HOME} && ${Constants.Docker.Commands.COMPOSE_UP_DETACHED}'",
                )
            }
        }

        outputHandler.handleMessage("Docker Compose services started on control nodes")
    }

    private fun startOtelOnCassandraNodes() {
        startOtelOnNodes(
            serverType = ServerType.Cassandra,
            otelConfigPath = Constants.ConfigPaths.CASSANDRA_OTEL_CONFIG,
            dockerComposePath = Constants.ConfigPaths.CASSANDRA_DOCKER_COMPOSE,
            remoteOtelConfigName = Constants.ConfigPaths.CASSANDRA_REMOTE_OTEL_CONFIG,
        )
    }

    private fun startOtelOnStressNodes() {
        startOtelOnNodes(
            serverType = ServerType.Stress,
            otelConfigPath = Constants.ConfigPaths.STRESS_OTEL_CONFIG,
            dockerComposePath = Constants.ConfigPaths.STRESS_DOCKER_COMPOSE,
            remoteOtelConfigName = Constants.ConfigPaths.STRESS_REMOTE_OTEL_CONFIG,
        )
    }

    private fun startOtelOnNodes(
        serverType: ServerType,
        otelConfigPath: String,
        dockerComposePath: String,
        remoteOtelConfigName: String,
    ) {
        val nodeTypeName = serverType.serverType
        outputHandler.handleMessage("Starting OTel collectors on $nodeTypeName nodes...")

        val otelConfigFile = File(otelConfigPath)
        val dockerComposeFile = File(dockerComposePath)

        if (!otelConfigFile.exists() || !dockerComposeFile.exists()) {
            outputHandler.handleMessage(
                "$nodeTypeName OTel config files not found, skipping OTel startup",
            )
            return
        }

        // Get the internal IP of the first control node for OTLP endpoint
        val controlHost = tfstate.getHosts(ServerType.Control).firstOrNull()
        if (controlHost == null) {
            outputHandler.handleMessage(
                "No control nodes found, skipping OTel startup for $nodeTypeName nodes",
            )
            return
        }
        val controlNodeIp = controlHost.private

        tfstate.withHosts(serverType, hosts, parallel = true) { host ->
            outputHandler.handleMessage(
                "Starting OTel collector on $nodeTypeName node ${host.alias} (${host.public})",
            )

            // Check if configuration files exist on the remote host
            val configCheckResult =
                remoteOps.executeRemotely(
                    host,
                    "test -f ${Constants.Paths.REMOTE_HOME}/$remoteOtelConfigName && " +
                        "test -f ${Constants.Paths.REMOTE_DOCKER_COMPOSE} ${Constants.RemoteChecks.FILE_EXISTS_SUFFIX}",
                )

            if (configCheckResult.text.trim() == Constants.RemoteChecks.NOT_FOUND_RESPONSE) {
                outputHandler.handleMessage(
                    "OTel configuration not found on ${host.alias}, skipping...",
                )
                return@withHosts
            }

            // Check if .env file exists, if not create it
            val envCheckResult =
                remoteOps.executeRemotely(
                    host,
                    "test -f ${Constants.Paths.REMOTE_ENV_FILE} ${Constants.RemoteChecks.FILE_EXISTS_SUFFIX}",
                )

            if (envCheckResult.text.trim() == Constants.RemoteChecks.NOT_FOUND_RESPONSE) {
                outputHandler.handleMessage("Creating .env file for ${host.alias}")
                val envContent =
                    """
                    CONTROL_NODE_IP=$controlNodeIp
                    """.trimIndent()

                remoteOps.executeRemotely(
                    host,
                    "cat > ${Constants.Paths.REMOTE_ENV_FILE} << 'EOF'\n$envContent\nEOF",
                )
            }

            // Check if docker is available
            try {
                val dockerCheck =
                    remoteOps.executeRemotely(host, Constants.Docker.Commands.VERSION_CHECK)
                outputHandler.handleMessage("Docker check on ${host.alias}: ${dockerCheck.text}")
            } catch (e: Exception) {
                outputHandler.handleMessage(
                    "Warning: Docker may not be installed on ${host.alias}: ${e.message}",
                )
                return@withHosts
            }

            // Pull OTel collector image
            outputHandler.handleMessage("Pulling OTel collector image on ${host.alias}...")
            try {
                val pullResult =
                    remoteOps.executeRemotely(
                        host,
                        "docker pull ${Constants.Docker.Images.OTEL_COLLECTOR}",
                    )
                outputHandler.handleMessage("Docker pull completed on ${host.alias}")
            } catch (e: Exception) {
                outputHandler.handleMessage(
                    "Warning: Failed to pull OTel image on ${host.alias}: ${e.message}",
                )
            }

            // Start OTel collector with docker compose (retry handled by RemoteOperationsService)
            try {
                remoteOps.executeRemotely(
                    host,
                    "cd ${Constants.Paths.REMOTE_HOME} && ${Constants.Docker.Commands.COMPOSE_UP_DETACHED}",
                )
                outputHandler.handleMessage("OTel collector started on ${host.alias}")
                // Check OTel collector status
                Thread.sleep(Constants.Time.OTEL_STARTUP_DELAY_MS) // Give it time to start
                try {
                    val statusResult =
                        remoteOps.executeRemotely(
                            host,
                            "docker ps | grep otel-collector",
                        )
                    if (statusResult.text.contains("Up")) {
                        outputHandler.handleMessage("OTel collector is running on ${host.alias}")
                    } else {
                        outputHandler.handleMessage(
                            "Warning: OTel collector may not be running properly on ${host.alias}",
                        )
                    }
                } catch (e: Exception) {
                    outputHandler.handleMessage(
                        "Could not verify OTel status on ${host.alias}: ${e.message}",
                    )
                }
            } catch (e: Exception) {
                outputHandler.handleMessage(
                    "ERROR: Failed to start OTel on ${host.alias}: ${e.message}",
                )
            }
        }

        outputHandler.handleMessage("OTel collectors startup completed on $nodeTypeName nodes")
    }

    /**
     * Called automatically, ignore that it's unused.
     */
    @PostExecute
    @Suppress("TooGenericExceptionCaught", "UnusedPrivateMember", "LongMethod")
    fun setupMcpTunnels() {
        if (!context.isMcp) {
            return
        }

        outputHandler.handleMessage("Setting up SSH tunnels for MCP mode...")

        val controlHost = tfstate.getHosts(ServerType.Control).firstOrNull()
        if (controlHost == null) {
            outputHandler.handleMessage("Warning: No control nodes found, cannot create MCP tunnels")
            return
        }

        val tunnelManager: SSHTunnelManager by inject()
        val tunnels = mutableListOf<SSHTunnel>()

        // Create MCP tunnel with error handling
        try {
            val mcpTunnel =
                tunnelManager.createTunnel(
                    host = controlHost,
                    remotePort = Constants.Network.EASY_CASS_MCP_PORT,
                    remoteHost = "localhost",
                    localPort = Constants.Network.EASY_CASS_MCP_PORT,
                )
            tunnels.add(mcpTunnel)
            val mcpTunnelInfo =
                "✓ MCP tunnel: localhost:${mcpTunnel.localPort} -> " +
                    "${controlHost.public}:${Constants.Network.EASY_CASS_MCP_PORT}"
            outputHandler.handleMessage(mcpTunnelInfo)
            outputHandler.handleMessage(
                "  easy-cass-mcp MCP server accessible at http://localhost:${mcpTunnel.localPort}/mcp",
            )
        } catch (e: Exception) {
            outputHandler.handleMessage("Error: Could not create MCP tunnel: ${e.message}")
        }

        // Create stress node tunnel
        val stressHost = tfstate.getHosts(ServerType.Stress).firstOrNull()
        if (stressHost != null) {
            try {
                val stressTunnel =
                    tunnelManager.createTunnel(
                        host = stressHost,
                        remotePort = Constants.Network.CASSANDRA_EASY_STRESS_PORT,
                        remoteHost = "localhost",
                        localPort = Constants.Network.CASSANDRA_EASY_STRESS_PORT,
                    )
                tunnels.add(stressTunnel)
                val stressTunnelInfo =
                    "✓ Stress tunnel: localhost:${stressTunnel.localPort} -> " +
                        "${stressHost.public}:${Constants.Network.CASSANDRA_EASY_STRESS_PORT}"
                outputHandler.handleMessage(stressTunnelInfo)
                outputHandler.handleMessage(
                    "  cassandra-easy-stress MCP server accessible at http://localhost:${stressTunnel.localPort}/mcp",
                )
            } catch (e: Exception) {
                outputHandler.handleMessage("Error: Could not create stress tunnel: ${e.message}")
            }
        } else {
            outputHandler.handleMessage("Warning: No stress nodes found, skipping stress tunnel creation")
        }

        // Summary
        if (tunnels.isNotEmpty()) {
            outputHandler.handleMessage("")
            outputHandler.handleMessage("SSH tunnels established successfully (${tunnels.size}/2)")
            outputHandler.handleMessage("Tunnels will remain active until easy-cass-lab process exits")
        } else {
            outputHandler.handleMessage("Warning: Failed to create any SSH tunnels")
        }
    }
}
