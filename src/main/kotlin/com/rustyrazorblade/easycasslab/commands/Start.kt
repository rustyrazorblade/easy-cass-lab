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
import com.rustyrazorblade.easycasslab.ssh.tunnel.SSHTunnel
import com.rustyrazorblade.easycasslab.ssh.tunnel.SSHTunnelManager
import org.koin.core.component.inject
import java.io.File

@McpCommand
@RequireDocker
@RequireSSHKey
@Parameters(commandDescription = "Start cassandra on all nodes via service command")
class Start(context: Context) : BaseCommand(context) {
    private val userConfig: User by inject()

    companion object {
        private const val DEFAULT_SLEEP_BETWEEN_STARTS_SECONDS = 120L
        private const val DOCKER_COMPOSE_STARTUP_DELAY_MS = 5000L
        private const val DOCKER_COMPOSE_RETRY_DELAY_MS = 2000L
        private const val DOCKER_COMPOSE_MAX_RETRIES = 3
    }

    @Parameter(names = ["--sleep"], description = "Time to sleep between starts in seconds")
    var sleep: Long = DEFAULT_SLEEP_BETWEEN_STARTS_SECONDS

    @ParametersDelegate var hosts = Hosts()

    override fun execute() {
        with(TermColors()) {
            tfstate.withHosts(ServerType.Cassandra, hosts) {
                outputHandler.handleMessage(green("Starting $it"))
                remoteOps.executeRemotely(it, "sudo systemctl start cassandra").text
                outputHandler.handleMessage("Cassandra started, waiting for up/normal")
                remoteOps.executeRemotely(it, "sudo wait-for-up-normal").text
            }
            tfstate.withHosts(ServerType.Cassandra, hosts, parallel = true) {
                remoteOps.executeRemotely(it, "sudo systemctl start cassandra-sidecar").text
            }
        }

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

        // this has to happen last, because we want Cassandra to be available before starting the
        // MCP server.
        deployDockerComposeToControlNodes()
    }

    private fun deployDockerComposeToControlNodes() {
        outputHandler.handleMessage("Starting Docker Compose services on control nodes...")

        val dockerComposeFile = File("control/docker-compose.yaml")
        val otelConfigFile = File("control/otel-collector-config.yaml")
        val dataPrepperConfigFile = File("control/data-prepper-pipelines.yaml")

        if (!dockerComposeFile.exists()) {
            throw RuntimeException("control/docker-compose.yaml not found - required file missing")
        }

        if (!otelConfigFile.exists()) {
            throw RuntimeException(
                "control/otel-collector-config.yaml not found - required file missing",
            )
        }

        if (!dataPrepperConfigFile.exists()) {
            throw RuntimeException(
                "control/data-prepper-pipelines.yaml not found - required file missing",
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
            remoteOps.upload(host, envFile.toPath(), "/home/ubuntu/.env")
            envFile.delete()

            // Upload required configuration files
            outputHandler.handleMessage("Uploading configuration files to ${host.public}...")
            remoteOps.upload(host, dockerComposeFile.toPath(), "/home/ubuntu/docker-compose.yaml")
            remoteOps.upload(
                host,
                otelConfigFile.toPath(),
                "/home/ubuntu/otel-collector-config.yaml",
            )
            remoteOps.upload(
                host,
                dataPrepperConfigFile.toPath(),
                "/home/ubuntu/data-prepper-pipelines.yaml",
            )
            // Check if docker and docker compose are available
            try {
                val dockerCheck =
                    remoteOps.executeRemotely(host, "which docker && docker --version")
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
                    remoteOps.executeRemotely(host, "cd /home/ubuntu && docker compose pull")
                outputHandler.handleMessage("Docker pull output: ${pullResult.text}")
            } catch (e: RuntimeException) {
                outputHandler.handleMessage("Warning: Failed to pull Docker images: ${e.message}")
                outputHandler.handleMessage(
                    "Will attempt to start anyway (images may be pulled automatically)...",
                )
            }

            // Run docker compose up in detached mode with retry logic
            var success = false
            var retryCount = 0
            var lastError: String? = null

            while (!success && retryCount < DOCKER_COMPOSE_MAX_RETRIES) {
                try {
                    if (retryCount > 0) {
                        outputHandler.handleMessage(
                            "Retrying docker compose up (attempt ${retryCount + 1}/${DOCKER_COMPOSE_MAX_RETRIES})...",
                        )
                        Thread.sleep(DOCKER_COMPOSE_RETRY_DELAY_MS)
                    }

                    // Run docker compose up
                    val result =
                        remoteOps.executeRemotely(
                            host,
                            "cd /home/ubuntu && docker compose up -d",
                        )
                    outputHandler.handleMessage("Docker Compose output: ${result.text}")
                    success = true
                } catch (e: RuntimeException) {
                    lastError = e.message
                    retryCount++
                    if (retryCount < DOCKER_COMPOSE_MAX_RETRIES) {
                        outputHandler.handleMessage("Docker compose failed: ${e.message}")
                        outputHandler.handleMessage(
                            "Will retry in ${DOCKER_COMPOSE_RETRY_DELAY_MS}ms...",
                        )
                    }
                }
            }

            if (!success) {
                outputHandler.handleMessage(
                    "ERROR: Failed to start Docker Compose after $DOCKER_COMPOSE_MAX_RETRIES attempts",
                )
                outputHandler.handleMessage("Last error: $lastError")
                outputHandler.handleMessage(
                    "You may need to manually run: ssh ${host.public} 'cd /home/ubuntu && docker compose up -d'",
                )
            }

            // Wait for services to be ready only if docker compose succeeded
            if (success) {
                outputHandler.handleMessage("Waiting for services to start...")
                Thread.sleep(DOCKER_COMPOSE_STARTUP_DELAY_MS) // Give services time to start

                // Check service status
                try {
                    val statusResult =
                        remoteOps.executeRemotely(host, "cd /home/ubuntu && docker compose ps")
                    outputHandler.handleMessage("Service status: ${statusResult.text}")
                } catch (e: Exception) {
                    outputHandler.handleMessage(
                        "Warning: Could not check service status: ${e.message}",
                    )
                }
            }
        }

        outputHandler.handleMessage("Docker Compose services started on control nodes")
    }



    private fun startOtelOnCassandraNodes() {
        outputHandler.handleMessage("Starting OTel collectors on Cassandra nodes...")

        val otelConfigFile = File("cassandra/otel-cassandra-config.yaml")
        val dockerComposeFile = File("cassandra/docker-compose.yaml")

        if (!otelConfigFile.exists() || !dockerComposeFile.exists()) {
            outputHandler.handleMessage(
                "Cassandra OTel config files not found, skipping OTel startup",
            )
            return
        }

        // Get the internal IP of the first control node for OTLP endpoint
        val controlHost = tfstate.getHosts(ServerType.Control).firstOrNull()
        if (controlHost == null) {
            outputHandler.handleMessage(
                "No control nodes found, skipping OTel startup for Cassandra nodes",
            )
            return
        }
        val controlNodeIp = controlHost.private

        tfstate.withHosts(ServerType.Cassandra, hosts, parallel = true) { host ->
            outputHandler.handleMessage(
                "Starting OTel collector on Cassandra node ${host.alias} (${host.public})",
            )

            // Check if configuration files exist on the remote host
            val configCheckResult =
                remoteOps.executeRemotely(
                    host,
                    "test -f /home/ubuntu/otel-cassandra-config.yaml && " +
                        "test -f /home/ubuntu/docker-compose.yaml && echo 'exists' || echo 'not found'",
                )

            if (configCheckResult.text.trim() == "not found") {
                outputHandler.handleMessage(
                    "OTel configuration not found on ${host.alias}, skipping...",
                )
                return@withHosts
            }

            // Check if .env file exists, if not create it
            val envCheckResult =
                remoteOps.executeRemotely(
                    host,
                    "test -f /home/ubuntu/.env && echo 'exists' || echo 'not found'",
                )

            if (envCheckResult.text.trim() == "not found") {
                outputHandler.handleMessage("Creating .env file for ${host.alias}")
                val envContent =
                    """
                    CONTROL_NODE_IP=$controlNodeIp
                    """.trimIndent()

                remoteOps.executeRemotely(
                    host,
                    "cat > /home/ubuntu/.env << 'EOF'\n$envContent\nEOF",
                )
            }

            // Check if docker is available
            try {
                val dockerCheck =
                    remoteOps.executeRemotely(host, "which docker && docker --version")
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
                        "docker pull otel/opentelemetry-collector-contrib:latest",
                    )
                outputHandler.handleMessage("Docker pull completed on ${host.alias}")
            } catch (e: Exception) {
                outputHandler.handleMessage(
                    "Warning: Failed to pull OTel image on ${host.alias}: ${e.message}",
                )
            }

            // Start OTel collector with docker compose
            var success = false
            var retryCount = 0
            var lastError: String? = null

            while (!success && retryCount < DOCKER_COMPOSE_MAX_RETRIES) {
                try {
                    if (retryCount > 0) {
                        outputHandler.handleMessage(
                            "Retrying OTel startup on ${host.alias} " +
                                "(attempt ${retryCount + 1}/${DOCKER_COMPOSE_MAX_RETRIES})...",
                        )
                        Thread.sleep(DOCKER_COMPOSE_RETRY_DELAY_MS)
                    }

                    // Run docker compose up for OTel
                    val result =
                        remoteOps.executeRemotely(
                            host,
                            "cd /home/ubuntu && docker compose up -d",
                        )
                    outputHandler.handleMessage("OTel collector started on ${host.alias}")
                    success = true
                } catch (e: RuntimeException) {
                    lastError = e.message
                    retryCount++
                    if (retryCount < DOCKER_COMPOSE_MAX_RETRIES) {
                        outputHandler.handleMessage(
                            "OTel startup failed on ${host.alias}: ${e.message}",
                        )
                    }
                }
            }

            if (!success) {
                outputHandler.handleMessage(
                    "ERROR: Failed to start OTel on ${host.alias} after $DOCKER_COMPOSE_MAX_RETRIES attempts",
                )
                outputHandler.handleMessage("Last error: $lastError")
            } else {
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
            }
        }

        outputHandler.handleMessage("OTel collectors startup completed on Cassandra nodes")
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
                "  MCP server accessible at http://localhost:${mcpTunnel.localPort}/sse",
            )

        } catch (e: Exception) {
            outputHandler.handleMessage("Error: Could not create MCP tunnel: ${e.message}")
        }

        // Create OpenSearch tunnel with error handling
        try {
            val opensearchTunnel =
                tunnelManager.createTunnel(
                    host = controlHost,
                    remotePort = Constants.Network.OPENSEARCH_PORT,
                    remoteHost = "localhost",
                    localPort = Constants.Network.OPENSEARCH_PORT,
                )
            tunnels.add(opensearchTunnel)
            val opensearchTunnelInfo =
                "✓ OpenSearch tunnel: localhost:${opensearchTunnel.localPort} -> " +
                    "${controlHost.public}:${Constants.Network.OPENSEARCH_PORT}"
            outputHandler.handleMessage(opensearchTunnelInfo)
            outputHandler.handleMessage(
                "  OpenSearch accessible at http://localhost:${opensearchTunnel.localPort}",
            )
        } catch (e: Exception) {
            outputHandler.handleMessage("Error: Could not create OpenSearch tunnel: ${e.message}")
        }

        // Create OpenSearch Dashboards tunnel with error handling
        try {
            val dashboardsTunnel =
                tunnelManager.createTunnel(
                    host = controlHost,
                    remotePort = Constants.Network.OPENSEARCH_DASHBOARDS_PORT,
                    remoteHost = "localhost",
                    localPort = Constants.Network.OPENSEARCH_DASHBOARDS_PORT,
                )
            tunnels.add(dashboardsTunnel)
            val dashboardsTunnelInfo =
                "✓ OpenSearch Dashboards tunnel: localhost:${dashboardsTunnel.localPort} -> " +
                    "${controlHost.public}:${Constants.Network.OPENSEARCH_DASHBOARDS_PORT}"
            outputHandler.handleMessage(dashboardsTunnelInfo)
            val dashboardsUrl = "http://localhost:${dashboardsTunnel.localPort}"
            outputHandler.handleMessage("  OpenSearch Dashboards accessible at $dashboardsUrl")
        } catch (e: Exception) {
            outputHandler.handleMessage(
                "Error: Could not create OpenSearch Dashboards tunnel: ${e.message}",
            )
        }

        // Summary
        if (tunnels.isNotEmpty()) {
            outputHandler.handleMessage("")
            outputHandler.handleMessage("SSH tunnels established successfully (${tunnels.size}/3)")
            outputHandler.handleMessage("Tunnels will remain active until easy-cass-lab process exits")
        } else {
            outputHandler.handleMessage("Warning: Failed to create any SSH tunnels")
        }
    }
}
