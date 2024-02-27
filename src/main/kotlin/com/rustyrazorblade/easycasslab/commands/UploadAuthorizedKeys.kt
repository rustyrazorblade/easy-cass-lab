package com.rustyrazorblade.easycasslab.commands

import com.beust.jcommander.Parameters
import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.configuration.ServerType
import org.apache.sshd.scp.client.ScpClient
import java.nio.file.Paths
import kotlin.io.path.exists

@Parameters(commandDescription = "upload authorized (public) keys from the ./authorized_keys directory")
class UploadAuthorizedKeys(val context: Context) : ICommand {

    override fun execute() {
        if (!Paths.get(sourceDir).exists()) {
            println("$sourceDir does not exist")
            System.exit(1)
        }

        context.tfstate.withHosts(ServerType.Cassandra) {
            context.executeRemotely(it, "mkdir -p $uploadDir")

            val scp = context.getScpClient(it)
            scp.upload(
                Paths.get(sourceDir),
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