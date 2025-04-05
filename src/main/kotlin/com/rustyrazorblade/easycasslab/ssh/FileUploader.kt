package com.rustyrazorblade.easycasslab.ssh

import com.rustyrazorblade.easycasslab.configuration.Host
import org.apache.logging.log4j.kotlin.logger
import org.apache.sshd.client.session.ClientSession
import java.nio.file.Path
import org.apache.sshd.scp.client.ScpClientCreator
import org.apache.sshd.scp.client.CloseableScpClient

/**
 * Uploads files to a remote host
 */
class FileUploader(private val session: ClientSession) {
    private val log = logger()
    
    /**
     * Upload a file to a remote host
     * 
     * @param host The target host
     * @param local The local file path
     * @param remote The remote file path
     */
    fun upload(local: Path, remote: String) {
        log.debug { "Uploading file ${local.toAbsolutePath()} to ${session}:$remote" }
        val creator = ScpClientCreator.instance()
        val client = creator.createScpClient(session)
        val scpClient = CloseableScpClient.singleSessionInstance(client)

        scpClient.upload(local, remote)
    }
}