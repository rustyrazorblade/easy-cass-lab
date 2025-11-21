package com.rustyrazorblade.easycasslab.commands

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import com.beust.jcommander.ParametersDelegate
import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.annotations.RequireProfileSetup
import com.rustyrazorblade.easycasslab.commands.delegates.Hosts
import com.rustyrazorblade.easycasslab.configuration.ServerType
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.io.path.exists

/** Downloads configuration. */
@RequireProfileSetup
@Parameters(commandDescription = "Download JVM and YAML config files.")
class DownloadConfig(
    context: Context,
) : BaseCommand(context) {
    @ParametersDelegate var hosts = Hosts()

    @Parameter(names = ["--version"], description = "Version to download, default is current")
    var version = "current"

    companion object {
        val logger = KotlinLogging.logger {}
    }

    override fun execute() {
        val cassandraHosts = tfstate.getHosts(ServerType.Cassandra)

        // Currently using first host - consider adding --host option for specific node selection
        val host = cassandraHosts.first()
        val resolvedVersion = remoteOps.getRemoteVersion(host, version)

        logger.info {
            "Original version: $version.  Resolved version: ${resolvedVersion.versionString}. "
        }
        val localDir = resolvedVersion.localDir.toFile()

        // Dont' overwrite.
        // Future enhancement: Add --force flag to overwrite and support
        // node-specific configurations
        if (!localDir.exists()) {
            localDir.mkdirs()

            remoteOps.downloadDirectory(
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
