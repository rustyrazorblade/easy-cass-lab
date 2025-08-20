package com.rustyrazorblade.easycasslab.commands

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import com.beust.jcommander.ParametersDelegate
import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.commands.delegates.Hosts
import com.rustyrazorblade.easycasslab.configuration.ServerType
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.io.path.exists

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
        val logger = KotlinLogging.logger {}
    }

    override fun execute() {
        val cassandraHosts = context.tfstate.getHosts(ServerType.Cassandra)

        // Currently using first host - consider adding --host option for specific node selection
        val host = cassandraHosts.first()
        val resolvedVersion = context.getRemoteVersion(host, version)

        logger.info { "Original version: $version.  Resolved version: ${resolvedVersion.versionString}. " }
        val localDir = resolvedVersion.localDir.toFile()

        // Dont' overwrite.
        // Future enhancement: Add --force flag to overwrite and support
        // node-specific configurations
        if (!localDir.exists()) {
            localDir.mkdirs()

            context.downloadDirectory(
                host,
                remoteDir = "${resolvedVersion.path}/conf",
                localDir = localDir,
                excludeFilters =
                    listOf(
                        "cassandra*.yaml",
                        "axonenv*",
                        "cassandra-rackdc.properties",
                    ),
            )
        }
    }
}
