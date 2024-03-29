package com.rustyrazorblade.easycasslab.commands

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.configuration.ServerType
import org.apache.sshd.scp.client.ScpClient
import java.nio.file.Paths
import kotlin.io.path.exists

@Parameters(commandDescription = "Upload authorized (public) keys from the ./authorized_keys directory")
class UploadAuthorizedKeys(val context: Context) : ICommand {

    @Parameter(descriptionKey = "Local directory of authorized keys")
    var localDir = "authorized_keys"

    override fun execute() {
        var path = Paths.get(localDir)
        if (!path.exists()) {
            println("$localDir does not exist")
            System.exit(1)
        }

        context.tfstate.withHosts(ServerType.Cassandra) {
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

    companion object {
        val sourceDir = "authorized_keys"
        val uploadDir = "~/.ssh/easy_cass_lab_authorized_keys"
    }
}