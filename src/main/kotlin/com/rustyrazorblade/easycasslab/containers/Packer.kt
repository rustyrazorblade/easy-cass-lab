package com.rustyrazorblade.easycasslab.containers

import com.github.dockerjava.api.model.AccessMode
import com.rustyrazorblade.easycasslab.Containers
import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.Docker
import com.rustyrazorblade.easycasslab.VolumeMapping
import org.apache.logging.log4j.kotlin.logger
import java.io.File
import kotlin.system.exitProcess

class Packer(val context: Context) {
    private val docker = Docker(context)

    private var containerWorkingDir = "/local"
    private var logger = logger()

    // todo include the region defined in the profile
    fun build(name: String) {
        val region = context.userConfig.region
        execute( "build", "-var", "region=$region", name)
    }

    private fun execute(vararg commands: String): Result<String> {
        docker.pullImage(Containers.PACKER)

        val args = commands.toMutableList()
        var localPackerPath = context.cwdPath + "/packer"
        if (!File(localPackerPath).exists()) {
            println("packer directory not found: $localPackerPath")
            exitProcess(1)
        }

        logger.info("Mounting $localPackerPath to $containerWorkingDir, starting with $args")

        // mount credentials
        // todo fix the CWD
        // get the main process and go up a directory
        val packerDir = VolumeMapping(localPackerPath, containerWorkingDir, AccessMode.ro)
        var creds = "/credentials"

        return docker
            .addVolume(packerDir)
            .addVolume(VolumeMapping(context.awsConfig.absolutePath, creds, AccessMode.ro))
            .addEnv("AWS_SHARED_CREDENTIALS_FILE=$creds")
            .runContainer(Containers.PACKER, args, containerWorkingDir)
    }
}