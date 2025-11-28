package com.rustyrazorblade.easycasslab.commands

import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.annotations.RequireProfileSetup
import com.rustyrazorblade.easycasslab.commands.mixins.HostsMixin
import com.rustyrazorblade.easycasslab.configuration.ServerType
import io.github.oshai.kotlinlogging.KotlinLogging
import picocli.CommandLine.Command
import picocli.CommandLine.Mixin
import picocli.CommandLine.Option

/**
 * Download JVM and YAML config files.
 */
@RequireProfileSetup
@Command(
    name = "download-config",
    aliases = ["dc"],
    description = ["Download JVM and YAML config files"],
)
class DownloadConfig(
    context: Context,
) : PicoBaseCommand(context) {
    @Mixin
    var hosts = HostsMixin()

    @Option(names = ["--version"], description = ["Version to download, default is current"])
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

        // Don't overwrite.
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
