package com.rustyrazorblade.easycasslab.ssh

import org.apache.logging.log4j.kotlin.logger
import org.apache.sshd.client.session.ClientSession
import org.apache.sshd.scp.client.CloseableScpClient
import java.io.File

/**
 * Downloads directories from a remote host
 */
class DirectoryDownloader(private val session: ClientSession,
                          private val scpClient: CloseableScpClient) {
    private val log = logger()
    
    /**
     * Download a directory from a remote host
     * 
     * @param host The source host
     * @param remoteDir The remote directory
     * @param localDir The local directory
     */
    fun download(remoteDir: String, localDir: File) {
        if (!localDir.exists()) {
            localDir.mkdirs()
        }
        
        log.debug { "Downloading directory from ${session}:$remoteDir to ${localDir.absolutePath}" }
        
        val fileListOutput = RemoteCommandExecutor(session).execute("find $remoteDir -type f")
        val remoteFiles = fileListOutput.split("\n").filter { it.isNotEmpty() }
        
        // Create file downloader
        val fileDownloader = FileDownloader(session, scpClient)
        
        // Download each file
        for (remoteFile in remoteFiles) {
            val relativePath = remoteFile.removePrefix("$remoteDir/")
            val localFile = File(localDir, relativePath)
            
            // Ensure parent directory exists
            localFile.parentFile.mkdirs()
            
            fileDownloader.download(remoteFile, localFile.toPath())
        }
    }
}