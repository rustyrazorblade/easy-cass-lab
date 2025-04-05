package com.rustyrazorblade.easycasslab.ssh

import org.apache.logging.log4j.kotlin.logger
import org.apache.sshd.client.session.ClientSession
import org.apache.sshd.scp.client.CloseableScpClient
import org.apache.sshd.scp.client.ScpClientCreator
import java.nio.file.Path

/**
 * Downloads files from a remote host
 */
class FileDownloader(private val session: ClientSession,
                     private val scpClient: CloseableScpClient) {
    private val log = logger()
    
    /**
     * Download a file from a remote host
     * 
     * @param host The source host
     * @param remote The remote file path
     * @param local The local file path
     */
    fun download(remote: String, local: Path) {
        log.debug { "Downloading file from ${session} ${remote} to ${local.toAbsolutePath()}" }
        scpClient.download(remote, local)
    }
}