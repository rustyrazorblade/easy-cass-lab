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
     * Close the connection
     */
    fun close() {}
}
