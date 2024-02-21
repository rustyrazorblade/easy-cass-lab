package com.rustyrazorblade.easycasslab.commands

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.configuration.ServerType
import java.nio.file.Path

@Parameters(commandDescription = "Upload the cassandra.yaml fragment to all nodes and apply to cassandra.yaml")
class UpdateConfig(val context: Context) : ICommand {
    @Parameter(names = ["--host"], descriptionKey = "Host to patch, optional")
    var host: String = ""

    @Parameter(descriptionKey = "Patch file to upload")
    var file: String = "cassandra.patch.yaml"

    override fun execute() {
        context.requireSshKey()
        // upload the patch file
        val cassandraHosts = context.tfstate.getHosts(ServerType.Cassandra)
        cassandraHosts.map {
            context.upload(it, Path.of(file), file)
        }
        // apply the patch
        cassandraHosts.map {
            println("Patching $it")
        }
    }

}