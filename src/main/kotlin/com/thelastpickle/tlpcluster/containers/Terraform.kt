package com.thelastpickle.tlpcluster.containers

import com.thelastpickle.tlpcluster.Context
import com.thelastpickle.tlpcluster.Docker
import com.github.dockerjava.api.model.AccessMode
import com.thelastpickle.tlpcluster.Containers
import com.thelastpickle.tlpcluster.VolumeMapping


class Terraform(val context: Context) {
    private val docker = Docker(context)

    private var localDirectory = "/local"

    fun init() : Result<String> {
        return execute("init")
    }


    fun up() : Result<String> {
        val commands = mutableListOf("apply")
        commands.add("-auto-approve")
        return execute(*commands.toTypedArray())
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
        return docker
                .addVolume(VolumeMapping(context.cwdPath, "/local", AccessMode.rw))
                .addVolume(VolumeMapping(context.terraformCacheDir.absolutePath, "/tcache", AccessMode.rw))
                .addEnv("TF_PLUGIN_CACHE_DIR=/tcache")
                .runContainer(Containers.TERRAFORM, args, localDirectory)
    }
}