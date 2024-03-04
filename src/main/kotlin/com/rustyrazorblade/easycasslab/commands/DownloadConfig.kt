package com.rustyrazorblade.easycasslab.commands

import com.beust.jcommander.Parameter
import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.configuration.ServerType
import java.nio.file.Path

/**
 * Downloads configuration.
 */
class DownloadConfig(val context: Context) : ICommand {
    @Parameter(names = ["--host"], descriptionKey = "Host to download from, optional")
    var host = ""

    @Parameter(names = ["--version"], descriptionKey = "Version to download, default is current")
    var version = "current"
    override fun execute() {
        // download jvm.options
        val cassandraHosts = context.tfstate.getHosts(ServerType.Cassandra)

        // TODO: add host option
        val host = cassandraHosts.first()

        var path = "/usr/local/cassandra/$version/conf/jvm.options"
        context.download(host, path, Path.of("jvm.options"))
    }
}