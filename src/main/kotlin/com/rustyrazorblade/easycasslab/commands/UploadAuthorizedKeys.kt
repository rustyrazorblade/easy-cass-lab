package com.rustyrazorblade.easycasslab.commands

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.configuration.Host
import com.rustyrazorblade.easycasslab.configuration.ServerType
import org.apache.sshd.scp.client.ScpClient
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists

@Parameters(commandDescription = "Upload authorized (public) keys from the ./authorized_keys directory")
class UploadAuthorizedKeys(val context: Context) : ICommand {

    @Parameter(descriptionKey = "Local directory of authorized keys")
    var localDir = "authorized_keys"

    @Parameter(description = "Hosts to run this on, leave blank for all hosts.", names = ["--hosts"])
    var hosts = ""

    override fun execute() {
        val path = Paths.get(localDir)
        if (!path.exists()) {
            println("$localDir does not exist")
            System.exit(1)
        }

        val upload = doUpload(path)
        context.tfstate.withHosts(ServerType.Cassandra, hosts) { upload(it) }
        context.tfstate.withHosts(ServerType.Stress, "") { upload(it) }
    }

    companion object {
        val sourceDir = "authorized_keys"
        val uploadDir = "~/.ssh/easy_cass_lab_authorized_keys"
    }

    private fun doUpload(path: Path) = { it: Host ->
        context.executeRemotely(it, "mkdir -p $uploadDir")

        val scp = context.getScpClient(it)
        scp.upload(
            path,
            uploadDir,
            ScpClient.Option.Recursive,
            ScpClient.Option.PreserveAttributes,
            ScpClient.Option.TargetIsDirectory
        )

        context.executeRemotely(it, "ls $uploadDir/$sourceDir/*.pub | xargs cat >> ~/.ssh/authorized_keys")
    }
}