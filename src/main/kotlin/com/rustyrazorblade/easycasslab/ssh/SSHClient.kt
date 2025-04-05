package com.rustyrazorblade.easycasslab.ssh

import org.apache.logging.log4j.kotlin.logger
import org.apache.sshd.client.session.ClientSession
import org.apache.sshd.scp.client.CloseableScpClient
import org.apache.sshd.scp.client.ScpClientCreator
import java.io.File
import java.nio.file.Path

/**
 * Main class for SSH operations
 * Acts as a facade for all SSH-related functionality
 */
class SSHClient(private val session: ClientSession) : ISSHClient {
    private val log = logger()

    /**
     * Execute a command on a remote host
     */
    override fun executeRemoteCommand(command: String, output: Boolean, secret: Boolean): String {
        return RemoteCommandExecutor(session).execute(command, output, secret)
    }
    
    /**
     * Upload a file to a remote host
     */
    override fun uploadFile(local: Path, remote: String) {
        FileUploader(session).upload(local, remote)
    }
    
    /**
     * Upload a directory to a remote host
     */
    override fun uploadDirectory(localDir: File, remoteDir: String) {
        DirectoryUploader(session).upload(localDir, remoteDir)
    }
    
    /**
     * Download a file from a remote host
     */
    override fun downloadFile(remote: String, local: Path) {
        FileDownloader(session).download(remote, local)
    }
    
    /**
     * Download a directory from a remote host
     */
    override fun downloadDirectory(remoteDir: String, localDir: File) {
        DirectoryDownloader(session).download(remoteDir, localDir)
    }

    override fun getScpClient(): CloseableScpClient {
        val creator = ScpClientCreator.instance()
        val client = creator.createScpClient(session)
        val scpClient = CloseableScpClient.singleSessionInstance(client)
        return scpClient
    }
    /**
     * Stop the SSH client
     */
    fun stop() {
        log.debug { "Stopping SSH client" }
        session.close()
    }

}