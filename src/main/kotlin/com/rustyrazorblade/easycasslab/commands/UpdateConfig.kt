package com.rustyrazorblade.easycasslab.commands

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import com.fasterxml.jackson.databind.node.ObjectNode
import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.configuration.ServerType
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.deleteExisting
import kotlin.io.path.inputStream

@Parameters(commandDescription = "Upload the cassandra.yaml fragment to all nodes and apply to cassandra.yaml.  Done automatically after use-cassandra.")
class UpdateConfig(val context: Context) : ICommand {
    @Parameter(names = ["--host"], descriptionKey = "Host to patch, optional")
    var host: String = ""

    @Parameter(descriptionKey = "Patch file to upload")
    var file: String = "cassandra.patch.yaml"

    @Parameter(names = ["--jvm"], descriptionKey = "jvm.options file to upload")
    var jvm = "jvm.options"

    @Parameter(names = ["--version"], descriptionKey = "Version to upload, default is current")
    var version = "current"

    @Parameter(names = ["--restart", "-r"], descriptionKey = "Restart cassandra after patching")
    var restart = false

    override fun execute() {
        context.requireSshKey()
        // upload the patch file
        val cassandraHosts = context.tfstate.getHosts(ServerType.Cassandra)
        cassandraHosts.map {
            println("Uploading $file to $it")

            val yaml = context.yaml.readTree(Path.of(file).inputStream())
            (yaml as ObjectNode).put("listen_address", it.private)
                                .put("rpc_address", it.private)

            println("Patching $it")
            val tmp = Files.createTempFile("easycasslab", "yaml")
            context.yaml.writeValue(tmp.toFile(), yaml)
            // call the patch command on the server
            context.upload(it, tmp, file)
            tmp.deleteExisting()

            context.executeRemotely(it, "/usr/local/bin/patch-config $file")

            // uploading jvm.options
            context.tfstate.getHosts(ServerType.Cassandra).map { host ->
                context.upload(host, Path.of(jvm), "jvm.options")
                context.executeRemotely(host, "sudo cp jvm.options /usr/local/cassandra/$version/conf/jvm.options")
                context.executeRemotely(host, "sudo chown -R cassandra:cassandra /usr/local/cassandra/$version/conf")
            }

            if (restart) {
                Restart(context).execute()
            }
        }
    }

}