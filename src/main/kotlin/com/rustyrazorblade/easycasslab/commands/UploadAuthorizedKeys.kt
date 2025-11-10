package com.rustyrazorblade.easycasslab.commands

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import com.beust.jcommander.ParametersDelegate
import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.commands.delegates.Hosts
import com.rustyrazorblade.easycasslab.configuration.Host
import com.rustyrazorblade.easycasslab.configuration.ServerType
import java.io.File
import java.io.FileFilter
import java.io.FileWriter
import java.nio.file.Paths
import kotlin.io.path.exists

@Parameters(
    commandDescription = "Upload authorized (public) keys from the ./authorized_keys directory",
)
class UploadAuthorizedKeys(
    context: Context,
) : BaseCommand(context) {
    @Parameter(descriptionKey = "Local directory of authorized keys")
    var localDir = "authorized_keys"

    @ParametersDelegate var hosts = Hosts()

    val authorizedKeysExtra = "~/.ssh/authorized_keys_extra"
    val authorizedKeys = "~/.ssh/authorized_keys"

    override fun execute() {
        val path = Paths.get(localDir)
        if (!path.exists()) {
            outputHandler.handleMessage("$localDir does not exist")
            System.exit(1)
        }

        var files =
            File(localDir).listFiles(FileFilter { it.name.endsWith(".pub") })
                ?: error("Failed to list files in $localDir")
        outputHandler.handleMessage("Files: ${files.map { it.name }}")

        // collect all the keys into a single file then upload
        val keys = files.joinToString("\n") { it.readText().trim() }

        val authorizedKeysExtra = File("authorized_keys_extra")
        FileWriter(authorizedKeysExtra).use {
            it.write(keys)
            it.write("\n")
        }

        outputHandler.handleMessage("Uploading the following keys:")
        outputHandler.handleMessage(keys)

        val upload = doUpload(authorizedKeysExtra)
        tfstate.withHosts(ServerType.Cassandra, hosts) { upload(it) }
        tfstate.withHosts(ServerType.Stress, Hosts.all()) { upload(it) }
    }

    private fun doUpload(authorizedKeys: File) =
        { host: Host ->
            // Upload the file using RemoteOperationsService
            remoteOps.upload(host, authorizedKeys.toPath(), authorizedKeysExtra)

            // Append to authorized_keys
            remoteOps
                .executeRemotely(
                    host,
                    "cat $authorizedKeysExtra >> /home/ubuntu/.ssh/authorized_keys",
                ).text
        }
}
