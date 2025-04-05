package com.rustyrazorblade.easycasslab.ssh

import com.rustyrazorblade.easycasslab.configuration.Host
import org.apache.logging.log4j.kotlin.logger
import org.apache.sshd.client.session.ClientSession
import java.io.File

/**
 * Uploads directories to a remote host
 */
class DirectoryUploader(
    private val sshClient: ClientSession
) {
    private val log = logger()
    private val fileUploader = FileUploader(sshClient)
    
    /**
     * Upload a directory to a remote host
     * 
     * @param host The target host
     * @param localDir The local directory
     * @param remoteDir The remote directory
     */
    fun upload(localDir: File, remoteDir: String) {
        if (!localDir.exists() || !localDir.isDirectory) {
            log.error { "Local directory $localDir does not exist or is not a directory" }
            return
        }
        
        log.debug { "Uploading directory ${localDir.absolutePath} to ${sshClient}:$remoteDir" }

        RemoteCommandExecutor(sshClient).execute("mkdir -p $remoteDir")

        // Process each file in the directory
        localDir.listFiles()?.forEach { file ->
            val relativePath = file.toRelativeString(localDir)
            val remotePath = "$remoteDir/$relativePath"
            
            if (file.isDirectory) {
                // Recursively upload subdirectories
                DirectoryUploader(sshClient).upload(file, remotePath)
            } else {
                // Upload individual file
                FileUploader(sshClient).upload(file.toPath(), remotePath)
            }
        }
    }
}