package com.rustyrazorblade.easycasslab.commands

import com.beust.jcommander.Parameter
import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.configuration.ServerType
import java.nio.file.Path
import kotlin.io.path.exists

/**
 * Downloads configuration.
 */
class DownloadConfig(val context: Context) : ICommand {
    @Parameter(names = ["--host"], descriptionKey = "Host to download from, optional")
    var host = ""

    @Parameter(names = ["--file", "-f"], descriptionKey = "Name of the file")
    var name = "jvm.options"

    @Parameter(names = ["--overwrite", "-o"], descriptionKey = "Overwrite the file if it exists")
    var overwrite = false

    @Parameter(names = ["--version"], descriptionKey = "Version to download, default is current")
    var version = "current"
    override fun execute() {
        val cassandraHosts = context.tfstate.getHosts(ServerType.Cassandra)

        // TODO: add host option
        val host = cassandraHosts.first()

        var path = "/usr/local/cassandra/$version/conf/jvm.options"
        var file = Path.of(name)
        println(file.toUri())

        if (!file.exists() || overwrite ) {
            println("Downloading jvm.options configuration file to $file")
            context.download(host, path, file)
        } else {
            println("$file exists and --overwrite is false.")
        }
    }
}