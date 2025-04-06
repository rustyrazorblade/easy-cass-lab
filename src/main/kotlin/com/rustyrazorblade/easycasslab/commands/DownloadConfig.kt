package com.rustyrazorblade.easycasslab.commands

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import com.beust.jcommander.ParametersDelegate
import com.github.ajalt.mordant.TermColors
import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.commands.delegates.Hosts
import com.rustyrazorblade.easycasslab.configuration.ServerType
import org.apache.logging.log4j.kotlin.logger
import java.io.File
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory

/**
 * Downloads configuration.
 */
@Parameters(commandDescription = "Download JVM and YAML config files.")
class DownloadConfig(val context: Context) : ICommand {
    @ParametersDelegate
    var hosts = Hosts()

    @Parameter(names = ["--version"], description = "Version to download, default is current")
    var version = "current"

    companion object {
        val logger = logger()
    }

    override fun execute() {
        val cassandraHosts = context.tfstate.getHosts(ServerType.Cassandra)

        // TODO: add host option
        val host = cassandraHosts.first()
        val resolvedVersion = context.getRemoteVersion(host, version)

        logger.info("Original version: $version.  Resolved version: ${resolvedVersion.version}. ")
        val localDir = resolvedVersion.localDir.toFile()
        if (!localDir.exists()) {
            localDir.mkdirs()
        }
        // TODO: exclude C* yaml
        context.downloadDirectory(host,
            remoteDir = "${resolvedVersion.path}/conf",
            localDir = localDir,
            excludeFilters = listOf(
                "cassandra*.yaml",
                "axonenv*",
                "cassandra-rackdc.properties"))
    }
}