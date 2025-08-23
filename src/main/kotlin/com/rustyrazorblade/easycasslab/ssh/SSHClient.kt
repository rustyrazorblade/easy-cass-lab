package com.rustyrazorblade.easycasslab.ssh

import com.rustyrazorblade.easycasslab.output.OutputHandler
import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.sshd.client.session.ClientSession
import org.apache.sshd.scp.client.CloseableScpClient
import org.apache.sshd.scp.client.ScpClientCreator
import java.io.File
import java.nio.file.Path

/**
 * Main class for SSH operations
 * Acts as a facade for all SSH-related functionality
 */
class SSHClient(
    private val session: ClientSession,
    private val outputHandler: OutputHandler,
) : ISSHClient {
    private val log = KotlinLogging.logger {}

    /**
     * Execute a command on a remote host
     *
     * @param command The command to execute
     * @param output Whether to print the command output
     * @param secret Whether the command contains sensitive information
     * @return The command output wrapped in a Response object
     */
    override fun executeRemoteCommand(
        command: String,
        output: Boolean,
        secret: Boolean,
    ): Response {
        // Create connection for this host
        if (!secret) {
            outputHandler.handleMessage("Executing remote command: $command")
        } else {
            outputHandler.handleMessage("Executing remote command: [hidden]")
        }

        val result = session.executeRemoteCommand(command)

        if (output) {
            outputHandler.handleMessage(result)
        }

        return Response(result)
    }

    /**
     * Upload a file to a remote host
     */
    override fun uploadFile(
        local: Path,
        remote: String,
    ) {
        outputHandler.handleMessage("Uploading file ${local.toAbsolutePath()} to $session:$remote")
        getScpClient().upload(local, remote)
    }

    /**
     * Upload a directory to a remote host
     */
    override fun uploadDirectory(
        localDir: File,
        remoteDir: String,
    ) {
        if (!localDir.exists() || !localDir.isDirectory) {
            log.error { "Local directory $localDir does not exist or is not a directory" }
            return
        }

        outputHandler.handleMessage("Uploading directory ${localDir.absolutePath} to $session:$remoteDir")

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
    override fun downloadFile(
        remote: String,
        local: Path,
    ) {
        log.debug { "Downloading file from $session $remote to ${local.toAbsolutePath()}" }
        getScpClient().download(remote, local)
    }

    /**
     * Download a directory from a remote host
     *
     * @param remoteDir The remote directory to download
     * @param localDir The local directory where files will be downloaded
     * @param includeFilters Optional list of patterns to filter files for download
     * @param excludeFilters Optional list of patterns to exclude files from download
     */
    override fun downloadDirectory(
        remoteDir: String,
        localDir: File,
        includeFilters: List<String>,
        excludeFilters: List<String>,
    ) {
        if (!localDir.exists()) {
            localDir.mkdirs()
        }

        log.debug { "Downloading directory from $session:$remoteDir to ${localDir.absolutePath}" }

        val fileListOutput = executeRemoteCommand("find $remoteDir -type f", false, false)
        val remoteFiles = fileListOutput.text.split("\n").filter { it.isNotEmpty() }

        // Download each file
        for (remoteFile in remoteFiles) {
            val relativePath = remoteFile.removePrefix("$remoteDir/")

            if (shouldDownloadFile(relativePath, includeFilters, excludeFilters)) {
                val localFile = File(localDir, relativePath)
                // Ensure parent directory exists
                localFile.parentFile.mkdirs()
                downloadFile(remoteFile, localFile.toPath())
            }
        }
    }

    private fun shouldDownloadFile(
        relativePath: String,
        includeFilters: List<String>,
        excludeFilters: List<String>,
    ): Boolean {
        val fileName = relativePath.substringAfterLast("/")

        // Check exclude filters
        val isExcluded =
            excludeFilters.isNotEmpty() &&
                excludeFilters.any { pattern ->
                    fileName.matches(pattern.replace("*", ".*").toRegex())
                }

        // Check include filters
        val isNotIncluded =
            includeFilters.isNotEmpty() &&
                !includeFilters.any { pattern ->
                    fileName.matches(pattern.replace("*", ".*").toRegex())
                }

        return !isExcluded && !isNotIncluded
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
