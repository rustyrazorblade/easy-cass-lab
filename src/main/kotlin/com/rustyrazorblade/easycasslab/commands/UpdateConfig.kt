package com.rustyrazorblade.easycasslab.commands

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import com.beust.jcommander.ParametersDelegate
import com.fasterxml.jackson.databind.node.ObjectNode
import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.annotations.McpCommand
import com.rustyrazorblade.easycasslab.annotations.RequireSSHKey
import com.rustyrazorblade.easycasslab.commands.delegates.Hosts
import com.rustyrazorblade.easycasslab.configuration.ServerType
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.deleteExisting
import kotlin.io.path.inputStream

@McpCommand
@RequireSSHKey
@Parameters(
    commandDescription =
        "Upload the cassandra.yaml fragment to all nodes and apply to cassandra.yaml. " +
            " Done automatically after use-cassandra.",
)
class UpdateConfig(context: Context) : BaseCommand(context) {
    @ParametersDelegate var hosts = Hosts()

    @Parameter(descriptionKey = "Patch file to upload")
    var file: String = "cassandra.patch.yaml"

    @Parameter(names = ["--version"], descriptionKey = "Version to upload, default is current")
    var version = "current"

    @Parameter(names = ["--restart", "-r"], descriptionKey = "Restart cassandra after patching")
    var restart = false

    override fun execute() {
        // upload the patch file
        tfstate.withHosts(ServerType.Cassandra, hosts) {
            outputHandler.handleMessage("Uploading $file to $it")

            val yaml = context.yaml.readTree(Path.of(file).inputStream())
            (yaml as ObjectNode).put("listen_address", it.private).put("rpc_address", it.private)

            outputHandler.handleMessage("Patching $it")
            val tmp = Files.createTempFile("easycasslab", "yaml")
            context.yaml.writeValue(tmp.toFile(), yaml)

            // call the patch command on the server
            remoteOps.upload(it, tmp, file)
            tmp.deleteExisting()
            val resolvedVersion = remoteOps.getRemoteVersion(it, version)
            remoteOps.executeRemotely(it, "/usr/local/bin/patch-config $file").text

            // Create a temporary directory on the remote filesystem using mktemp
            val tempDir =
                remoteOps.executeRemotely(it, "mktemp -d -t easycasslab.XXXXXX").text.trim()
            outputHandler.handleMessage("Created temporary directory $tempDir on $it")

            // Upload files to the temporary directory first
            outputHandler.handleMessage(
                "Uploading configuration files to temporary directory $tempDir",
            )
            remoteOps.uploadDirectory(it, resolvedVersion.file, tempDir)

            // Make sure the destination directory exists
            remoteOps.executeRemotely(it, "sudo mkdir -p ${resolvedVersion.conf}").text

            // Copy files from temp directory to the final location
            outputHandler.handleMessage(
                "Copying files from temporary directory to ${resolvedVersion.conf}",
            )
            remoteOps.executeRemotely(it, "sudo cp -R $tempDir/* ${resolvedVersion.conf}/").text

            // Change ownership of all files
            remoteOps.executeRemotely(
                it,
                "sudo chown -R cassandra:cassandra ${resolvedVersion.conf}",
            )
                .text

            // Clean up the temporary directory
            remoteOps.executeRemotely(it, "rm -rf $tempDir").text

            outputHandler.handleMessage("Configuration updated for $it")
        }

        if (restart) {
            val restart = Restart(context)
            restart.hosts = hosts
            restart.execute()
        }
    }
}
