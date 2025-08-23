package com.rustyrazorblade.easycasslab.containers

import com.github.dockerjava.api.model.AccessMode
import com.rustyrazorblade.easycasslab.Constants
import com.rustyrazorblade.easycasslab.Containers
import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.Docker
import com.rustyrazorblade.easycasslab.VolumeMapping
import io.github.oshai.kotlinlogging.KotlinLogging
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.parameter.parametersOf

class Terraform(val context: Context) : KoinComponent {
    private val docker: Docker by inject { parametersOf(context) }

    private var localDirectory = Constants.Paths.LOCAL_MOUNT
    private var logger = KotlinLogging.logger {}

    fun init(): Result<String> {
        return execute("init")
    }

    fun up(): Result<String> {
        val commands = mutableListOf("apply", Constants.Terraform.AUTO_APPROVE_FLAG).toTypedArray()
        // Spread operator is required to pass array to vararg parameter
        @Suppress("SpreadOperator")
        return execute(*commands)
    }

    fun down(autoApprove: Boolean): Result<String> {
        val commands = mutableListOf("destroy")
        if (autoApprove) {
            commands.add(Constants.Terraform.AUTO_APPROVE_FLAG)
        }
        // Spread operator is required to pass array to vararg parameter
        @Suppress("SpreadOperator")
        return execute(*commands.toTypedArray())
    }

    private fun execute(vararg command: String): Result<String> {
        val args = command.toMutableList()
        docker.pullImage(Containers.TERRAFORM)
        var mount = "/${context.awsCredentialsName}"
        logger.info { "Mounting credentials at ${context.awsConfig.absolutePath}:$mount" }

        return docker
            .addVolume(VolumeMapping(context.cwdPath, Constants.Paths.LOCAL_MOUNT, AccessMode.rw))
            .addVolume(
                VolumeMapping(
                    context.terraformCacheDir.absolutePath,
                    Constants.Paths.TERRAFORM_CACHE,
                    AccessMode.rw,
                ),
            )
            .addVolume(VolumeMapping(context.awsConfig.absolutePath, mount, AccessMode.ro))
            .addEnv("${Constants.Terraform.PLUGIN_CACHE_DIR_ENV}=${Constants.Paths.TERRAFORM_CACHE}")
            .runContainer(Containers.TERRAFORM, args, localDirectory)
    }
}
