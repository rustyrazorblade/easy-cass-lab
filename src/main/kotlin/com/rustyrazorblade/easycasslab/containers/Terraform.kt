package com.rustyrazorblade.easycasslab.containers

import  com.rustyrazorblade.easycasslab.Context
import  com.rustyrazorblade.easycasslab.Docker
import com.github.dockerjava.api.model.AccessMode
import  com.rustyrazorblade.easycasslab.Containers
import  com.rustyrazorblade.easycasslab.VolumeMapping


class Terraform(val context: Context) {
    private val docker = Docker(context)

    private var localDirectory = "/local"

    fun init() : Result<String> {
        return execute("init")
    }


    fun up() : Result<String> {
        val commands = mutableListOf("apply", "-auto-approve").toTypedArray()
        return execute(*commands)
    }


    fun down(autoApprove: Boolean) : Result<String> {
        val commands = mutableListOf("destroy")
        if(autoApprove) {
            commands.add("-auto-approve")
        }
        return execute(*commands.toTypedArray())
    }


    private fun execute(vararg command: String) : Result<String> {
        val args = command.toMutableList()
        docker.pullImage(Containers.TERRAFORM)
        return docker
                .addVolume(VolumeMapping(context.cwdPath, "/local", AccessMode.rw))
                .addVolume(VolumeMapping(context.terraformCacheDir.absolutePath, "/tcache", AccessMode.rw))
                .addEnv("TF_PLUGIN_CACHE_DIR=/tcache")
                .runContainer(Containers.TERRAFORM, args, localDirectory)
    }
}