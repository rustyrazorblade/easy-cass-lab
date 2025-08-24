package com.rustyrazorblade.easycasslab.commands

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import com.beust.jcommander.ParametersDelegate
import com.github.ajalt.mordant.TermColors
import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.commands.delegates.Hosts
import com.rustyrazorblade.easycasslab.configuration.ServerType
import java.io.File

@Parameters(commandDescription = "Start cassandra on all nodes via service command")
class Start(context: Context) : BaseCommand(context) {
    companion object {
        private const val DEFAULT_SLEEP_BETWEEN_STARTS_SECONDS = 120L
    }

    @Parameter(names = ["--sleep"], description = "Time to sleep between starts in seconds")
    var sleep: Long = DEFAULT_SLEEP_BETWEEN_STARTS_SECONDS

    @ParametersDelegate
    var hosts = Hosts()

    override fun execute() {
        context.requireSshKey()

        // Deploy and start Docker Compose services on control nodes first
        deployDockerComposeToControlNodes()

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
    }

    private fun deployDockerComposeToControlNodes() {
        outputHandler.handleMessage("Starting Docker Compose services on control nodes...")
        
        val dockerComposeFile = File("control/docker-compose.yaml")
        if (!dockerComposeFile.exists()) {
            outputHandler.handleMessage("control/docker-compose.yaml not found, skipping Docker Compose startup")
            return
        }
        
        context.tfstate.withHosts(ServerType.Control, hosts) { host ->
            outputHandler.handleMessage("Starting Docker Compose services on control node ${host.public}")
            
            // Check if docker-compose.yaml exists on the remote host
            val checkResult = remoteOps.executeRemotely(host, "test -f /home/ubuntu/docker-compose.yaml && echo 'exists' || echo 'not found'")
            
            if (checkResult.text.trim() == "not found") {
                outputHandler.handleMessage("docker-compose.yaml not found on ${host.public}, uploading...")
                // Upload docker-compose.yaml to ubuntu user's home directory
                remoteOps.upload(host, dockerComposeFile.toPath(), "/home/ubuntu/docker-compose.yaml")
            }
            
            // Run docker compose up in detached mode
            val result = remoteOps.executeRemotely(host, "cd /home/ubuntu && docker compose up -d")
            outputHandler.handleMessage("Docker Compose output: ${result.text}")
            
            // Wait for services to be ready
            outputHandler.handleMessage("Waiting for services to start...")
            Thread.sleep(5000) // Give services 5 seconds to start
            
            // Check service status
            val statusResult = remoteOps.executeRemotely(host, "cd /home/ubuntu && docker compose ps")
            outputHandler.handleMessage("Service status: ${statusResult.text}")
        }
        
        outputHandler.handleMessage("Docker Compose services started on control nodes")
    }
}
