package com.rustyrazorblade.easycasslab.commands

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import com.beust.jcommander.ParametersDelegate
import com.github.ajalt.mordant.TermColors
import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.commands.delegates.Hosts
import com.rustyrazorblade.easycasslab.configuration.ServerType
import com.rustyrazorblade.easycasslab.containers.Terraform
import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.sshd.common.SshException
import java.io.File
import java.nio.file.Path
import java.time.Duration

@Parameters(commandDescription = "Starts instances")
class Up(
    context: Context,
) : BaseCommand(context) {
    companion object {
        private val log = KotlinLogging.logger {}
        private val SSH_STARTUP_DELAY = Duration.ofSeconds(5)
        private val SSH_RETRY_DELAY = Duration.ofSeconds(1)
    }

    @Parameter(names = ["--no-setup", "-n"])
    var noSetup = false

    @ParametersDelegate
    var hosts = Hosts()

    override fun execute() {
        // we have to list both the variable files explicitly here
        // even though we have a terraform.tvars
        // we need the local one to apply at the highest priority
        // specifying the user one makes it take priority over the local one
        // so we have to explicitly specify the local one to ensure it gets
        // priority over user
        // slowly migrating code from Terraform to Java.
        context.cloudProvider.createLabEnvironment()
        provisionInfrastructure()
        writeConfigurationFiles()
        WriteConfig(context).execute()
        waitForSshAndDownloadVersions()
        uploadDockerComposeToControlNodes()
        setupInstancesIfNeeded()
    }

    private fun provisionInfrastructure() {
        val terraform = Terraform(context)
        with(TermColors()) {
            terraform.up().onFailure {
                log.error(it) { "Terraform provisioning failed" }
                outputHandler.handleError(it.message ?: "Unknown error")
                outputHandler.handleMessage(
                    "${red(
                        "Some resources may have been unsuccessfully provisioned.",
                    )}  Rerun ${green("easy-cass-lab up")} to provision the remaining resources.",
                )
            }.onSuccess {
                outputHandler.handleMessage(
                    """Instances have been provisioned.
                
                Use ${green("easy-cass-lab list")} to see all available versions
                
                Then use ${green("easy-cass-lab use <version>")} to use a specific version of Cassandra.  
                
                    """.trimMargin(),
                )
                outputHandler.handleMessage("Writing ssh config file to sshConfig.")
                outputHandler.handleMessage(
                    """The following alias will allow you to easily work with the cluster:
                |
                |${green("source env.sh")}
                |
                |
                    """.trimMargin(),
                )
                outputHandler.handleMessage(
                    "You can edit ${green(
                        "cassandra.patch.yaml",
                    )} with any changes you'd like to see merge in into the remote cassandra.yaml file.",
                )
            }
        }
    }

    private fun writeConfigurationFiles() {
        val config = File("sshConfig").bufferedWriter()
        context.tfstate.writeSshConfig(config)
        val envFile = File("env.sh").bufferedWriter()
        context.tfstate.writeEnvironmentFile(envFile)
        writeStressEnvironmentVariables()
    }

    private fun writeStressEnvironmentVariables() {
        val stressEnvironmentVars = File("environment.sh").bufferedWriter()
        stressEnvironmentVars.write("#!/usr/bin/env bash")
        stressEnvironmentVars.newLine()
        val host = context.tfstate.getHosts(ServerType.Cassandra).first().private
        stressEnvironmentVars.write("export  CASSANDRA_EASY_STRESS_CASSANDRA_HOST=$host")
        stressEnvironmentVars.newLine()
        stressEnvironmentVars.write("export  CASSANDRA_EASY_STRESS_PROM_PORT=0")
        stressEnvironmentVars.newLine()
        stressEnvironmentVars.write(
            "export CASSANDRA_EASY_STRESS_DEFAULT_DC=\$(curl -s " +
                "http://169.254.169.254/latest/dynamic/instance-identity/document | yq .region)",
        )
        stressEnvironmentVars.newLine()
        stressEnvironmentVars.flush()
        stressEnvironmentVars.close()
    }

    private fun waitForSshAndDownloadVersions() {
        outputHandler.handleMessage("Waiting for SSH to come up..")
        Thread.sleep(SSH_STARTUP_DELAY.toMillis())

        var done = false
        do {
            try {
                context.tfstate.withHosts(ServerType.Cassandra, hosts) {
                    remoteOps.executeRemotely(it, "echo 1").text
                    // download /etc/cassandra_versions.yaml if we don't have it yet
                    if (!File("cassandra_versions.yaml").exists()) {
                        remoteOps.download(it, "/etc/cassandra_versions.yaml", Path.of("cassandra_versions.yaml"))
                    }
                }
                done = true
            } catch (ignored: SshException) {
                outputHandler.handleMessage("SSH still not up yet, waiting..")
                Thread.sleep(SSH_RETRY_DELAY.toMillis())
            }
        } while (!done)
    }

    private fun uploadDockerComposeToControlNodes() {
        outputHandler.handleMessage("Preparing Docker Compose configuration for control nodes...")
        
        val dockerComposeFile = File("control/docker-compose.yaml")
        if (!dockerComposeFile.exists()) {
            outputHandler.handleMessage("control/docker-compose.yaml not found, skipping upload")
            return
        }
        
        // Get the internal IP of the first Cassandra node
        val cassandraHost = context.tfstate.getHosts(ServerType.Cassandra).first().private
        outputHandler.handleMessage("Using Cassandra host IP: $cassandraHost")
        
        // Read the docker-compose.yaml file and replace cassandra0 with the actual IP
        val dockerComposeContent = dockerComposeFile.readText()
        val updatedContent = dockerComposeContent.replace("CASSANDRA_HOST=cassandra0", "CASSANDRA_HOST=$cassandraHost")
        
        // Write the updated content back to the file
        dockerComposeFile.writeText(updatedContent)
        outputHandler.handleMessage("Updated docker-compose.yaml with Cassandra IP: $cassandraHost")
        
        context.tfstate.withHosts(ServerType.Control, hosts) { host ->
            outputHandler.handleMessage("Uploading docker-compose.yaml to control node ${host.public}")
            
            // Upload docker-compose.yaml to ubuntu user's home directory
            remoteOps.upload(host, dockerComposeFile.toPath(), "/home/ubuntu/docker-compose.yaml")
        }
        
        outputHandler.handleMessage("Docker Compose configuration uploaded to control nodes")
    }

    private fun setupInstancesIfNeeded() {
        if (noSetup) {
            with(TermColors()) {
                outputHandler.handleMessage(
                    "Skipping node setup.  You will need to run " +
                        "${green("easy-cass-lab setup-instance")} to complete setup",
                )
            }
        } else {
            SetupInstance(context).execute()

            if (context.userConfig.axonOpsKey.isNotBlank() && context.userConfig.axonOpsOrg.isNotBlank()) {
                outputHandler.handleMessage("Setting up axonops for ${context.userConfig.axonOpsOrg}")
                ConfigureAxonOps(context).execute()
            }
        }
    }
}
