package com.rustyrazorblade.easycasslab.commands

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import com.beust.jcommander.ParametersDelegate
import com.github.ajalt.mordant.TermColors
import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.annotations.RequireDocker
import com.rustyrazorblade.easycasslab.commands.delegates.Hosts
import com.rustyrazorblade.easycasslab.configuration.ServerType
import java.io.File

@RequireDocker
@Parameters(commandDescription = "Start cassandra on all nodes via service command")
class Start(context: Context) : BaseCommand(context) {
    companion object {
        private const val DEFAULT_SLEEP_BETWEEN_STARTS_SECONDS = 120L
        private const val DOCKER_COMPOSE_STARTUP_DELAY_MS = 5000L
        private const val DOCKER_COMPOSE_RETRY_DELAY_MS = 2000L
        private const val DOCKER_COMPOSE_MAX_RETRIES = 3
    }

    @Parameter(names = ["--sleep"], description = "Time to sleep between starts in seconds")
    var sleep: Long = DEFAULT_SLEEP_BETWEEN_STARTS_SECONDS

    @ParametersDelegate
    var hosts = Hosts()

    override fun execute() {
        context.requireSshKey()

        with(TermColors()) {
            context.tfstate.withHosts(ServerType.Cassandra, hosts) {
                outputHandler.handleMessage(green("Starting $it"))
                remoteOps.executeRemotely(it, "sudo systemctl start cassandra").text
                outputHandler.handleMessage("Cassandra started, waiting for up/normal")
                remoteOps.executeRemotely(it, "sudo wait-for-up-normal").text
                remoteOps.executeRemotely(it, "sudo systemctl start cassandra-sidecar").text
            }
        }

        if (context.userConfig.axonOpsOrg.isNotBlank() && context.userConfig.axonOpsKey.isNotBlank()) {
            StartAxonOps(context).execute()
        }

        // Write MCP configuration file for AI agents
        writeMCPConfiguration()
        // this has to happen last, because we want Cassandra to be available before starting the MCP server.
        deployDockerComposeToControlNodes()
    }

    private fun deployDockerComposeToControlNodes() {
        outputHandler.handleMessage("Starting Docker Compose services on control nodes...")

        val dockerComposeFile = File("control/docker-compose.yaml")
        val otelConfigFile = File("control/otel-collector-config.yaml")

        if (!dockerComposeFile.exists()) {
            outputHandler.handleMessage("control/docker-compose.yaml not found, skipping Docker Compose startup")
            return
        }

        context.tfstate.withHosts(ServerType.Control, hosts) { host ->
            outputHandler.handleMessage("Starting Docker Compose services on control node ${host.public}")

            // Check if docker-compose.yaml exists on the remote host
            val checkResult =
                remoteOps.executeRemotely(
                    host,
                    "test -f /home/ubuntu/docker-compose.yaml && echo 'exists' || echo 'not found'",
                )

            if (checkResult.text.trim() == "not found") {
                outputHandler.handleMessage("docker-compose.yaml not found on ${host.public}, uploading...")
                // Upload docker-compose.yaml to ubuntu user's home directory
                remoteOps.upload(host, dockerComposeFile.toPath(), "/home/ubuntu/docker-compose.yaml")
            }

            // Check and upload otel-collector-config.yaml if it exists
            if (otelConfigFile.exists()) {
                val otelCheckResult =
                    remoteOps.executeRemotely(
                        host,
                        "test -f /home/ubuntu/otel-collector-config.yaml && echo 'exists' || echo 'not found'",
                    )
                if (otelCheckResult.text.trim() == "not found") {
                    outputHandler.handleMessage("otel-collector-config.yaml not found on ${host.public}, uploading...")
                    remoteOps.upload(host, otelConfigFile.toPath(), "/home/ubuntu/otel-collector-config.yaml")
                }
            }

            // Check if docker and docker compose are available
            try {
                val dockerCheck = remoteOps.executeRemotely(host, "which docker && docker --version")
                outputHandler.handleMessage("Docker check: ${dockerCheck.text}")
            } catch (e: Exception) {
                outputHandler.handleMessage("Warning: Docker may not be installed on ${host.public}")
                outputHandler.handleMessage("Error: ${e.message}")
            }

            // Pull Docker images before starting
            outputHandler.handleMessage("Pulling Docker images on ${host.public}...")
            try {
                val pullResult = remoteOps.executeRemotely(host, "cd /home/ubuntu && docker compose pull")
                outputHandler.handleMessage("Docker pull output: ${pullResult.text}")
            } catch (e: Exception) {
                outputHandler.handleMessage("Warning: Failed to pull Docker images: ${e.message}")
                outputHandler.handleMessage("Will attempt to start anyway (images may be pulled automatically)...")
            }

            // Run docker compose up in detached mode with retry logic
            var success = false
            var retryCount = 0
            var lastError: String? = null
            
            while (!success && retryCount < DOCKER_COMPOSE_MAX_RETRIES) {
                try {
                    if (retryCount > 0) {
                        outputHandler.handleMessage("Retrying docker compose up (attempt ${retryCount + 1}/${DOCKER_COMPOSE_MAX_RETRIES})...")
                        Thread.sleep(DOCKER_COMPOSE_RETRY_DELAY_MS)
                    }
                    
                    // Run docker compose up
                    val result = remoteOps.executeRemotely(host, "cd /home/ubuntu && docker compose up -d")
                    outputHandler.handleMessage("Docker Compose output: ${result.text}")
                    success = true
                } catch (e: Exception) {
                    lastError = e.message
                    retryCount++
                    if (retryCount < DOCKER_COMPOSE_MAX_RETRIES) {
                        outputHandler.handleMessage("Docker compose failed: ${e.message}")
                        outputHandler.handleMessage("Will retry in ${DOCKER_COMPOSE_RETRY_DELAY_MS}ms...")
                    }
                }
            }
            
            if (!success) {
                outputHandler.handleMessage("ERROR: Failed to start Docker Compose after $DOCKER_COMPOSE_MAX_RETRIES attempts")
                outputHandler.handleMessage("Last error: $lastError")
                outputHandler.handleMessage("You may need to manually run: ssh ${host.public} 'cd /home/ubuntu && docker compose up -d'")
            }

            // Wait for services to be ready only if docker compose succeeded
            if (success) {
                outputHandler.handleMessage("Waiting for services to start...")
                Thread.sleep(DOCKER_COMPOSE_STARTUP_DELAY_MS) // Give services time to start

                // Check service status
                try {
                    val statusResult = remoteOps.executeRemotely(host, "cd /home/ubuntu && docker compose ps")
                    outputHandler.handleMessage("Service status: ${statusResult.text}")
                } catch (e: Exception) {
                    outputHandler.handleMessage("Warning: Could not check service status: ${e.message}")
                }
            }
        }

        outputHandler.handleMessage("Docker Compose services started on control nodes")
    }

    @Suppress("TooGenericExceptionCaught")
    private fun writeMCPConfiguration() {
        outputHandler.handleMessage("Writing MCP configuration file for AI agents...")

        val mcpConfig = """{
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
            outputHandler.handleMessage("AI agents can now connect to the MCP server at http://localhost:8000")
            outputHandler.handleMessage("To use with Claude Code, reference this configuration file in your .mcp.json")
        } catch (e: Exception) {
            outputHandler.handleMessage("Warning: Could not write MCP configuration file: ${e.message}")
        }
    }
}
