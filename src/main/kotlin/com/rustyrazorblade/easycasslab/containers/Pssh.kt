package com.rustyrazorblade.easycasslab.containers

import com.github.dockerjava.api.model.AccessMode
import  com.rustyrazorblade.easycasslab.*
import  com.rustyrazorblade.easycasslab.configuration.ServerType

import org.apache.logging.log4j.kotlin.logger


/**
 * This is currently flawed in that it only allows for SSH'ing to Cassandra
 */
class Pssh(val context: Context) {
    var sshKey =  context.userConfig.sshKeyPath
    private val provisionCommand = "cd provisioning; chmod +x install.sh; sudo ./install.sh"

    val log = logger()

    fun copyProvisioningResources(nodeType: ServerType) : Result<String> {
        return execute("copy_provisioning_resources.sh", "", nodeType)
    }

    fun provisionNode(nodeType: ServerType) : Result<String> {
        return execute("parallel_ssh.sh", "$provisionCommand ${nodeType.serverType}", nodeType)
    }

    fun startService(nodeType: ServerType, serviceName: String) : Result<String> {
        return serviceCommand(nodeType, serviceName, "start")
    }

    fun stopService(nodeType: ServerType, serviceName: String) : Result<String> {
        return serviceCommand(nodeType, serviceName, "stop")
    }

    private fun serviceCommand(nodeType: ServerType, serviceName: String, command: String) : Result<String> {
        return execute("parallel_ssh.sh",
                "sudo service $serviceName $command ",
                nodeType)
    }

    private fun execute(scriptName: String, scriptCommand: String, nodeType: ServerType) : Result<String> {
        val docker = Docker(context)

        val hosts = "PSSH_HOSTNAMES=${context.tfstate.getHosts(nodeType).map { it.public }.joinToString(" ")}"
        log.info("Starting container with $hosts")

        return docker
                .addVolume(VolumeMapping(sshKey, "/root/.ssh/aws-private-key", AccessMode.ro))
                .also {
                    log.info("Added $sshKey to container")
                }
                .addVolume(VolumeMapping(context.cwdPath, "/local", AccessMode.rw))
                .also {
                    log.info("Added ${context.cwdPath} to /local")
                }
                .addEnv(hosts)
                .runContainer(Containers.PSSH, mutableListOf("/usr/local/bin/$scriptName", scriptCommand), "")
    }

    init {
        // pull container
    }
}