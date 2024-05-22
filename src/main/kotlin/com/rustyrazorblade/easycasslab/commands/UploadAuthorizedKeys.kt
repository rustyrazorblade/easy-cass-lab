package com.rustyrazorblade.easycasslab.commands

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.configuration.Host
import com.rustyrazorblade.easycasslab.configuration.ServerType
import org.apache.sshd.scp.client.ScpClient
import java.io.File
import java.io.FileFilter
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists

@Parameters(commandDescription = "Upload authorized (public) keys from the ./authorized_keys directory")
class UploadAuthorizedKeys(val context: Context) : ICommand {

    @Parameter(descriptionKey = "Local directory of authorized keys")
    var localDir = "authorized_keys"

    @Parameter(description = "Hosts to run this on, leave blank for all hosts.", names = ["--hosts"])
    var hosts = ""

    val authorizedKeysExtra = "~/.ssh/authorized_keys_extra"
    val authorizedKeys = "~/.ssh/authorized_keys"

    override fun execute() {
        val path = Paths.get(localDir)
        if (!path.exists()) {
            println("$localDir does not exist")
            System.exit(1)
        }

        // collect all the keys into a single file then upload
        val keys = File(localDir).listFiles(FileFilter { it.name.endsWith(".pub") })!!
            .joinToString("\n") { it.readText().trim() }

        val authorizedKeys = File("authorized_keys_extra").apply {
            writeText(keys)
            writeText("\n")
        }

        println("Uploading the following keys:")
        println(authorizedKeys)

        val upload = doUpload(authorizedKeys)
        context.tfstate.withHosts(ServerType.Cassandra, hosts) { upload(it) }
        context.tfstate.withHosts(ServerType.Stress, "") { upload(it) }
    }

    private fun doUpload(authorizedKeys: File) = { it: Host ->
        val scp = context.getScpClient(it)

        scp.upload(
            authorizedKeys.path,
            authorizedKeysExtra,
            ScpClient.Option.PreserveAttributes,
        )

        context.executeRemotely(it, "cat $authorizedKeysExtra >> /home/ubuntu/.ssh/authorized_keys")
    }
}