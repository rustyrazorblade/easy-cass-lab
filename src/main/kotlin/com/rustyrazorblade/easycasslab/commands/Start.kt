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
import com.rustyrazorblade.easycasslab.mcp.RemoteMcpDiscovery
import com.rustyrazorblade.easycasslab.mcp.SshTunnelManager
import com.rustyrazorblade.easycasslab.providers.ssh.SSHConnectionProvider
import io.github.oshai.kotlinlogging.KotlinLogging
import org.koin.core.component.inject
import java.io.File

@McpCommand
@RequireDocker
@RequireSSHKey
@Parameters(commandDescription = "Start cassandra on all nodes via service command")
class Start(context: Context) : BaseCommand(context) {
    private val userConfig: User by inject()

    companion object {
        private val log = KotlinLogging.logger {}
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

        // Write MCP configuration file for AI agents
        writeMCPConfiguration()
        // this has to happen last, because we want Cassandra to be available before starting the
        // MCP server.
        deployDockerComposeToControlNodes()

        // Set up SS4O configuration after Docker services are started
        setupSS4OConfiguration()
    }

    private fun deployDockerComposeToControlNodes() {
        outputHandler.handleMessage("Starting Docker Compose services on control nodes...")

        val dockerComposeFile = File("control/docker-compose.yaml")
        val otelConfigFile = File("control/otel-collector-config.yaml")
        val dataPrepperConfigFile = File("control/data-prepper-pipelines.yaml")
        val ss4oTemplateFile = File("control/ss4o_metrics.json")

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

        if (!ss4oTemplateFile.exists()) {
            throw RuntimeException("control/ss4o_metrics.json not found - required file missing")
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
            remoteOps.upload(host, ss4oTemplateFile.toPath(), "/home/ubuntu/ss4o_metrics.json")

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

    private fun setupSS4OConfiguration() {
        outputHandler.handleMessage(
            "Setting up SS4O (Simple Schema for Observability) configuration...",
        )

        tfstate.withHosts(ServerType.Control, hosts) { host ->
            try {
                // Wait for OpenSearch to be ready
                outputHandler.handleMessage(
                    "Waiting for OpenSearch to be ready on ${host.public}...",
                )
                var opensearchReady = false
                var retryCount = 0
                val maxRetries = 12 // 2 minutes with 10-second intervals

                while (!opensearchReady && retryCount < maxRetries) {
                    try {
                        val healthCheck =
                            remoteOps.executeRemotely(
                                host,
                                "curl -s http://127.0.0.1:9200/_cluster/health",
                            )
                        if (healthCheck.text.contains("\"status\":\"yellow\"") ||
                            healthCheck.text.contains("\"status\":\"green\"")
                        ) {
                            opensearchReady = true
                            outputHandler.handleMessage("OpenSearch is ready on ${host.public}")
                        } else {
                            Thread.sleep(10000) // Wait 10 seconds
                            retryCount++
                        }
                    } catch (e: Exception) {
                        Thread.sleep(10000) // Wait 10 seconds
                        retryCount++
                    }
                }

                if (!opensearchReady) {
                    outputHandler.handleMessage(
                        "Warning: OpenSearch did not become ready on ${host.public}, skipping SS4O setup",
                    )
                    return@withHosts
                }

                // Create SS4O index template
                outputHandler.handleMessage("Creating SS4O index template on ${host.public}...")
                val createTemplateCommand =
                    """
                    curl -X PUT "http://127.0.0.1:9200/_index_template/ss4o_metrics" \
                    -H "Content-Type: application/json" \
                    -d '{
                        "index_patterns": ["ss4o_metrics-*"],
                        "template": {
                            "settings": {
                                "number_of_shards": 1,
                                "number_of_replicas": 0,
                                "index": {
                                    "refresh_interval": "5s"
                                }
                            },
                            "mappings": {
                                "properties": {
                                    "@timestamp": { "type": "date" },
                                    "observedTimestamp": { "type": "date" },
                                    "serviceName": { "type": "keyword" },
                                    "name": { "type": "keyword" },
                                    "description": { "type": "text" },
                                    "unit": { "type": "keyword" },
                                    "value": { "type": "double" },
                                    "kind": { "type": "keyword" },
                                    "aggregationTemporality": { "type": "keyword" },
                                    "startTime": { "type": "date" },
                                    "schemaUrl": { "type": "keyword" }
                                }
                            }
                        },
                        "priority": 100,
                        "version": 1,
                        "_meta": {
                            "description": "SS4O metrics index template for OpenSearch Observability"
                        }
                    }'
                    """.trimIndent()

                val templateResult = remoteOps.executeRemotely(host, createTemplateCommand)
                if (templateResult.text.contains("\"acknowledged\":true")) {
                    outputHandler.handleMessage(
                        "SS4O index template created successfully on ${host.public}",
                    )
                } else {
                    outputHandler.handleMessage(
                        "Warning: Failed to create SS4O index template on ${host.public}: ${templateResult.text}",
                    )
                }

                // Wait for OpenSearch Dashboards to be ready
                outputHandler.handleMessage(
                    "Waiting for OpenSearch Dashboards to be ready on ${host.public}...",
                )
                var dashboardsReady = false
                retryCount = 0

                while (!dashboardsReady && retryCount < maxRetries) {
                    try {
                        val dashboardsCheck =
                            remoteOps.executeRemotely(
                                host,
                                "curl -s http://127.0.0.1:5601/api/status",
                            )
                        if (dashboardsCheck.text.contains("\"level\":\"available\"") ||
                            dashboardsCheck.text.contains("overall")
                        ) {
                            dashboardsReady = true
                            outputHandler.handleMessage(
                                "OpenSearch Dashboards is ready on ${host.public}",
                            )
                        } else {
                            Thread.sleep(10000) // Wait 10 seconds
                            retryCount++
                        }
                    } catch (e: Exception) {
                        Thread.sleep(10000) // Wait 10 seconds
                        retryCount++
                    }
                }

                if (!dashboardsReady) {
                    outputHandler.handleMessage(
                        "Warning: OpenSearch Dashboards did not become ready on ${host.public}, skipping index pattern creation",
                    )
                    return@withHosts
                }

                // Create index pattern in OpenSearch Dashboards
                outputHandler.handleMessage(
                    "Creating SS4O index pattern in OpenSearch Dashboards on ${host.public}...",
                )
                val createIndexPatternCommand =
                    """
                    curl -X POST "http://127.0.0.1:5601/api/saved_objects/index-pattern/ss4o_metrics-*" \
                    -H "Content-Type: application/json" \
                    -H "osd-xsrf: true" \
                    -d '{
                        "attributes": {
                            "title": "ss4o_metrics-*",
                            "timeFieldName": "@timestamp"
                        }
                    }'
                    """.trimIndent()

                val indexPatternResult = remoteOps.executeRemotely(host, createIndexPatternCommand)
                if (indexPatternResult.text.contains("\"id\":\"ss4o_metrics-*\"") ||
                    indexPatternResult.text.contains(
                        "version_conflict_engine_exception",
                    )
                ) {
                    outputHandler.handleMessage(
                        "SS4O index pattern created successfully on ${host.public}",
                    )
                } else {
                    outputHandler.handleMessage(
                        "Info: Index pattern creation response on ${host.public}: ${indexPatternResult.text}",
                    )
                }

                // Restart Data Prepper to pick up the new template
                outputHandler.handleMessage(
                    "Restarting Data Prepper to apply SS4O configuration on ${host.public}...",
                )
                remoteOps.executeRemotely(host, "cd /home/ubuntu && docker restart data-prepper")

                outputHandler.handleMessage("SS4O configuration completed on ${host.public}")
            } catch (e: Exception) {
                outputHandler.handleMessage("Error setting up SS4O on ${host.public}: ${e.message}")
            }
        }

        outputHandler.handleMessage("SS4O configuration setup completed on all control nodes")
        outputHandler.handleMessage(
            "Metrics should now be visible in OpenSearch Dashboards -> Observability -> Metrics",
        )
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

    @Suppress("TooGenericExceptionCaught")
    private fun writeMCPConfiguration() {
        outputHandler.handleMessage("Writing MCP configuration file for AI agents...")

        val mcpConfig =
            """{
  "mcpServers": {
    "easy-cass-lab": {
      "type": "sse",
      "url": "http://localhost:8000/mcp",
      "description": "Easy Cass Lab MCP server for Cassandra cluster management"
    }
  }
}"""

        val configFile = File("easy-cass-mcp.json")
        try {
            configFile.writeText(mcpConfig)
            outputHandler.handleMessage("MCP configuration written to easy-cass-mcp.json")
            outputHandler.handleMessage(
                "AI agents can now connect to the MCP server at http://localhost:8000",
            )
            outputHandler.handleMessage(
                "To use with Claude Code, reference this configuration file in your .mcp.json",
            )
        } catch (e: Exception) {
            outputHandler.handleMessage(
                "Warning: Could not write MCP configuration file: ${e.message}",
            )
        }
    }

    /**
     * Post-execution hook that runs after the Start command completes.
     * When in MCP mode, this discovers remote MCP servers on control nodes and establishes SSH tunnels.
     */
    @PostExecute
    fun discoverRemoteMcpServers() {
        if (!context.isMcpMode) {
            return
        }

        try {
            log.info { "Starting remote MCP server discovery (MCP mode is active)" }

            val discovery = RemoteMcpDiscovery(context)
            val remoteServers = discovery.discoverRemoteServers()

            if (remoteServers.isEmpty()) {
                log.warn { "No remote MCP servers discovered on control nodes" }
                return
            }

            log.info { "Discovered ${remoteServers.size} remote MCP servers:" }
            remoteServers.forEach { server ->
                log.info { "  - ${server.nodeName} at ${server.endpoint}" }
            }

            // Establish SSH tunnels for remote MCP communication
            log.info { "Establishing SSH tunnels for remote MCP servers..." }
            val tunnelManager: SshTunnelManager by inject()
            val tunnels = tunnelManager.establishTunnels(remoteServers)

            if (tunnels.isEmpty()) {
                log.warn { "No SSH tunnels were established" }
            } else {
                log.info { "Established ${tunnels.size} SSH tunnels:" }
                tunnels.forEach { (nodeName, tunnel) ->
                    log.info {
                        "  - $nodeName: localhost:${tunnel.localPort} -> " +
                            "${tunnel.remoteHost}:${tunnel.remotePort}"
                    }
                }

                log.info { "SSH tunnels established and ready for MCP communication" }
            }
        } catch (e: Exception) {
            log.error(e) { "Failed to discover remote MCP servers or establish tunnels" }
            // Don't fail the Start command if MCP discovery/tunnel setup fails
        }
    }
}
