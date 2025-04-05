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
        log.debug { "Uploading file ${local.toAbsolutePath()} to ${session}:$remote" }
        getScpClient().upload(local, remote)
    }
    
    /**
     * Upload a directory to a remote host
     */
    override fun uploadDirectory(localDir: File, remoteDir: String) {
        if (!localDir.exists() || !localDir.isDirectory) {
            log.error { "Local directory $localDir does not exist or is not a directory" }
            return
        }
        
        log.debug { "Uploading directory ${localDir.absolutePath} to ${session}:$remoteDir" }

        executeRemoteCommand("mkdir -p $remoteDir", false, false)

        // Process each file in the directory
        localDir.listFiles()?.forEach { file ->
            val relativePath = file.toRelativeString(localDir)
            val remotePath = "$remoteDir/$relativePath"
            
            if (file.isDirectory) {
                // Recursively upload subdirectories
                uploadDirectory(file, remotePath)
            } else {
                // Upload individual file
                uploadFile(file.toPath(), remotePath)
            }
        }
    }
    
    /**
     * Download a file from a remote host
     */
    override fun downloadFile(remote: String, local: Path) {
        log.debug { "Downloading file from ${session} ${remote} to ${local.toAbsolutePath()}" }
        getScpClient().download(remote, local)
    }
    
    /**
     * Download a directory from a remote host
     */
    override fun downloadDirectory(remoteDir: String, localDir: File) {
        if (!localDir.exists()) {
            localDir.mkdirs()
        }
        
        log.debug { "Downloading directory from ${session}:$remoteDir to ${localDir.absolutePath}" }
        
        val fileListOutput = executeRemoteCommand("find $remoteDir -type f", false, false)
        val remoteFiles = fileListOutput.split("\n").filter { it.isNotEmpty() }
        
        // Download each file
        for (remoteFile in remoteFiles) {
            val relativePath = remoteFile.removePrefix("$remoteDir/")
            val localFile = File(localDir, relativePath)
            
            // Ensure parent directory exists
            localFile.parentFile.mkdirs()
            
            downloadFile(remoteFile, localFile.toPath())
        }
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
    override fun close() {
        log.debug { "Stopping SSH client" }
        session.close()
    }

}