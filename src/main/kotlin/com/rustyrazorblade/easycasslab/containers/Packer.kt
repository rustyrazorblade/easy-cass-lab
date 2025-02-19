package com.rustyrazorblade.easycasslab.containers

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.github.dockerjava.api.model.AccessMode
import com.rustyrazorblade.easycasslab.Containers
import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.Docker
import com.rustyrazorblade.easycasslab.VolumeMapping
import com.rustyrazorblade.easycasslab.commands.delegates.BuildArgs
import com.rustyrazorblade.easycasslab.configuration.CassandraVersion
import org.apache.logging.log4j.kotlin.logger
import java.io.File
import kotlin.system.exitProcess
import kotlin.io.path.createTempDirectory
import org.apache.commons.io.FileUtils
import java.nio.file.Path


class Packer(val context: Context, var directory: String) {
    private val docker = Docker(context)

    private var containerWorkingDir = "/local"
    private var logger = logger()
    private var release = false

    // todo include the region defined in the profile
    fun build(name: String, buildArgs: BuildArgs) {
        val command = mutableListOf("build",
            "-var", "region=${buildArgs.region}",
            "-var", "arch=${buildArgs.arch.type}")

        if (buildArgs.release) {
            // When passing the release flag,
            // we use the release version as the image version.
            // We also make the AMI public.
            release = true
            command.addAll(
                arrayOf("-var", "release_version=${context.version}"))
        }

        command.add(name)

        // refactor to exit with status 1 if the Result is failure
        val result = execute(*command.toTypedArray())
        when {
            result.isFailure -> {
                logger.error("Packer build failed: ${result.exceptionOrNull()}")
                exitProcess(1)
            }
            result.isSuccess -> {
                logger.info("Packer build succeeded")
            }
        }
    }

    private fun execute(vararg commands: String): Result<String> {
        docker.pullImage(Containers.PACKER)

        val args = commands.toMutableList()

        var localPackerPath = context.packerHome + directory

        if (!File(localPackerPath).exists()) {
            println("packer directory not found: $localPackerPath")
            exitProcess(1)
        }

        val tempDir = createTempDirectory().toFile()
        FileUtils.copyDirectory(File(localPackerPath), tempDir)
        logger.info("Copied packer files from $localPackerPath to $tempDir")

        if (!release && directory == "cassandra") {
            // if we're doing a C* image, we
            val initial = Path.of(localPackerPath, "cassandra_versions.yaml")
            val extras = context.cassandraVersionsExtra.toPath()
            logger.info("Loading files in $extras")

            val versions = CassandraVersion.loadFromMainAndExtras(initial, extras)
            val outputFile = File(tempDir, "cassandra_versions.yaml")
            CassandraVersion.write(versions, outputFile)
            logger.info("Written updated versions to $outputFile")
        }

        logger.info("Mounting $tempDir to $containerWorkingDir, starting with $args")

        // mount credentials
        // get the main process and go up a directory
        val packerDir = VolumeMapping(tempDir.absolutePath, containerWorkingDir, AccessMode.ro)
        var creds = "/credentials"

        return docker
            .addVolume(packerDir)
            .addVolume(VolumeMapping(context.awsConfig.absolutePath, creds, AccessMode.ro))
            .addEnv("AWS_SHARED_CREDENTIALS_FILE=$creds")
            .runContainer(Containers.PACKER, args, containerWorkingDir)
    }

}