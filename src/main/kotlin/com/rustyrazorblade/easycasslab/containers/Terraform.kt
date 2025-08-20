package com.rustyrazorblade.easycasslab.containers

import com.github.dockerjava.api.model.AccessMode
import com.rustyrazorblade.easycasslab.Containers
import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.Docker
import com.rustyrazorblade.easycasslab.VolumeMapping
import io.github.oshai.kotlinlogging.KotlinLogging

class Terraform(val context: Context) {
    private val docker = Docker(context)

    private var localDirectory = "/local"
    private var logger = KotlinLogging.logger {}

    fun init(): Result<String> {
        return execute("init")
    }

    fun up(): Result<String> {
        val commands = mutableListOf("apply", "-auto-approve").toTypedArray()
        // Spread operator is required to pass array to vararg parameter
        @Suppress("SpreadOperator")
        return execute(*commands)
    }

    fun down(autoApprove: Boolean): Result<String> {
        val commands = mutableListOf("destroy")
        if (autoApprove) {
            commands.add("-auto-approve")
        }
        // Spread operator is required to pass array to vararg parameter
        @Suppress("SpreadOperator")
        return execute(*commands.toTypedArray())
    }

    private fun execute(vararg command: String): Result<String> {
        val args = command.toMutableList()
        docker.pullImage(Containers.TERRAFORM)
        var mount = "/${context.awsCredentialsName}"
        logger.info("Mounting credentials at ${context.awsConfig.absolutePath}:$mount")

        return docker
            .addVolume(VolumeMapping(context.cwdPath, "/local", AccessMode.rw))
            .addVolume(VolumeMapping(context.terraformCacheDir.absolutePath, "/tcache", AccessMode.rw))
            .addVolume(VolumeMapping(context.awsConfig.absolutePath, mount, AccessMode.ro))
            .addEnv("TF_PLUGIN_CACHE_DIR=/tcache")
            .runContainer(Containers.TERRAFORM, args, localDirectory)
    }
}
