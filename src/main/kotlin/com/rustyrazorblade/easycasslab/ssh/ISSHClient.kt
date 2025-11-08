package com.rustyrazorblade.easycasslab.ssh

import org.apache.sshd.scp.client.CloseableScpClient
import java.io.File
import java.nio.file.Path

/**
 * Interface for SSH operations
 * Allows for testing with mock implementations
 */
interface ISSHClient {
    /**
     * Execute a command on a remote host
     */
    fun executeRemoteCommand(
        command: String,
        output: Boolean,
        secret: Boolean,
    ): Response

    /**
     * Upload a file to a remote host
     */
    fun uploadFile(
        local: Path,
        remote: String,
    )

    /**
     * Upload a directory to a remote host
     */
    fun uploadDirectory(
        localDir: File,
        remoteDir: String,
    )

    /**
     * Download a file from a remote host
     */
    fun downloadFile(
        remote: String,
        local: Path,
    )

    /**
     * Download a directory from a remote host
     *
     * @param remoteDir The remote directory to download
     * @param localDir The local directory where files will be downloaded
     * @param includeFilters Optional list of patterns to filter files for download
     * @param excludeFilters Optional list of patterns to exclude files from download
     */
    fun downloadDirectory(
        remoteDir: String,
        localDir: File,
        includeFilters: List<String> = listOf(),
        excludeFilters: List<String> = listOf(),
    )

    /**
     * Get an SCP client for this connection
     */
    fun getScpClient(): CloseableScpClient

    /**
     * Create a local port forward (SSH tunnel)
     *
     * @param localPort Local port to bind to (0 for auto-assign)
     * @param remoteHost Remote host to forward to
     * @param remotePort Remote port to forward to
     * @return The actual local port that was bound
     */
    fun createLocalPortForward(
        localPort: Int = 0,
        remoteHost: String = "localhost",
        remotePort: Int,
    ): Int

    /**
     * Close a local port forward
     *
     * @param localPort The local port of the forward to close
     */
    fun closeLocalPortForward(localPort: Int)

    /**
     * Check if the underlying SSH session is still open and authenticated.
     *
     * @return true if the session is open and authenticated, false otherwise
     */
    fun isSessionOpen(): Boolean

    /**
     * Close the connection
     */
    fun close() {}
}
