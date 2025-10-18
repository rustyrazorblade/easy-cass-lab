package com.rustyrazorblade.easycasslab.ssh

import com.rustyrazorblade.easycasslab.output.OutputHandler
import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.sshd.client.session.ClientSession
import org.apache.sshd.client.session.forward.ExplicitPortForwardingTracker
import org.apache.sshd.common.util.net.SshdSocketAddress
import org.apache.sshd.scp.client.CloseableScpClient
import org.apache.sshd.scp.client.ScpClientCreator
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.Charset
import java.nio.file.Path

/**
 * Main class for SSH operations
 * Acts as a facade for all SSH-related functionality
 * 
 * Thread-safety: All operations are synchronized to prevent concurrent access
 * to the underlying Apache SSHD ClientSession, which is not thread-safe.
 */
class SSHClient(
    private val session: ClientSession,
) : ISSHClient, KoinComponent {
    private val outputHandler: OutputHandler by inject()
    private val log = KotlinLogging.logger {}
    
    // Synchronization lock to ensure thread-safe access to the session
    // This allows multiple SSHClient instances (different hosts) to operate in parallel
    // while serializing operations on the same session (same host)
    private val operationLock = Any()

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
    ): Response = synchronized(operationLock) {
        // Create connection for this host
        if (!secret) {
            outputHandler.handleMessage("Executing remote command: $command")
        } else {
            outputHandler.handleMessage("Executing remote command: [hidden]")
        }

        val stderrStream = ByteArrayOutputStream()
        val result = session.executeRemoteCommand(command, stderrStream, Charset.defaultCharset())

        if (output) {
            outputHandler.handleMessage(result)
        }

        return@synchronized Response(result, stderrStream.toString())
    }

    /**
     * Upload a file to a remote host
     */
    override fun uploadFile(
        local: Path,
        remote: String,
    ): Unit = synchronized(operationLock) {
        outputHandler.handleMessage("Uploading file ${local.toAbsolutePath()} to $session:$remote")
        getScpClient().upload(local, remote)
    }

    /**
     * Upload a directory to a remote host
     */
    override fun uploadDirectory(
        localDir: File,
        remoteDir: String,
    ): Unit = synchronized(operationLock) {
        if (!localDir.exists() || !localDir.isDirectory) {
            log.error { "Local directory $localDir does not exist or is not a directory" }
            return@synchronized
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
    ): Unit = synchronized(operationLock) {
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
    ): Unit = synchronized(operationLock) {
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

    override fun getScpClient(): CloseableScpClient = synchronized(operationLock) {
        val creator = ScpClientCreator.instance()
        val client = creator.createScpClient(session)
        val scpClient = CloseableScpClient.singleSessionInstance(client)
        return@synchronized scpClient
    }

    // Track active port forwards for cleanup
    private val activePortForwards = mutableMapOf<Int, ExplicitPortForwardingTracker>()

    override fun createLocalPortForward(
        localPort: Int,
        remoteHost: String,
        remotePort: Int
    ): Int = synchronized(operationLock) {
        val localAddress = SshdSocketAddress("", localPort) // Empty string binds to all interfaces
        val remoteAddress = SshdSocketAddress(remoteHost, remotePort)

        log.info { "Creating port forward: localhost:$localPort -> $remoteHost:$remotePort" }

        val tracker = session.createLocalPortForwardingTracker(localAddress, remoteAddress)
        val actualPort = tracker.boundAddress.port

        activePortForwards[actualPort] = tracker

        log.info { "Port forward created: localhost:$actualPort -> $remoteHost:$remotePort" }
        return@synchronized actualPort
    }

    override fun closeLocalPortForward(localPort: Int): Unit = synchronized(operationLock) {
        activePortForwards[localPort]?.let { tracker ->
            try {
                tracker.close()
                activePortForwards.remove(localPort)
                log.info { "Closed port forward on localhost:$localPort" }
            } catch (e: Exception) {
                log.error(e) { "Error closing port forward on localhost:$localPort" }
            }
        }
    }

    /**
     * Stop the SSH client
     */
    override fun close(): Unit = synchronized(operationLock) {
        log.debug { "Stopping SSH client" }

        // Close all active port forwards
        activePortForwards.values.toList().forEach { tracker ->
            try {
                tracker.close()
            } catch (e: Exception) {
                log.debug(e) { "Error closing port forward during session cleanup" }
            }
        }
        activePortForwards.clear()

        session.close()
    }
}
