package com.rustyrazorblade.easycasslab.commands

import com.beust.jcommander.Parameter
import com.beust.jcommander.ParametersDelegate
import com.github.ajalt.mordant.TermColors
import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.commands.delegates.Hosts
import com.rustyrazorblade.easycasslab.configuration.ServerType
import java.nio.file.Path
import kotlin.io.path.exists

/**
 * Downloads configuration.
 */
class DownloadConfig(val context: Context) : ICommand {
    @ParametersDelegate
    var hosts = Hosts()

    @Parameter(names = ["--file", "-f"], description = "Name of the file")
    var name = "jvm.options"

    @Parameter(names = ["--overwrite", "-o"], description = "Overwrite the file if it exists")
    var overwrite = false

    @Parameter(names = ["--version"], description = "Version to download, default is current")
    var version = "current"
    override fun execute() {
        val cassandraHosts = context.tfstate.getHosts(ServerType.Cassandra)

        // TODO: add host option
        val host = cassandraHosts.first()

        var path = "/usr/local/cassandra/$version/conf/jvm.options"
        var file = Path.of(name)
        println(file.toUri())

        with(TermColors()) {
            if (!file.exists() || overwrite) {
                println("Downloading jvm.options configuration file to $file")
                context.download(host, path, file)
            } else {
                println(green("$file exists and --overwrite is false, not overwriting."))
            }
        }
    }
}