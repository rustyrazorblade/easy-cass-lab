package com.rustyrazorblade.easycasslab.ssh

import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.sshd.client.session.ClientSession
import org.apache.sshd.scp.client.CloseableScpClient
import org.apache.sshd.scp.client.ScpClient
import org.apache.sshd.scp.common.helpers.ScpTimestampCommandDetails
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission

/**
 * Mock implementation of ISSHClient for testing
 */
class MockSSHClient : ISSHClient {
    private val log = KotlinLogging.logger {}
    val executedCommands = mutableListOf<String>()
    val uploadedFiles = mutableListOf<Pair<Path, String>>()
    val uploadedDirectories = mutableListOf<Pair<File, String>>()
    val downloadedFiles = mutableListOf<Pair<String, Path>>()
    val downloadedDirectories = mutableListOf<Pair<String, File>>()

    /**
     * Mock command output can be customized per test
     */
    var commandOutput = ""

    override fun executeRemoteCommand(
        command: String,
        output: Boolean,
        secret: Boolean,
    ): Response {
        log.debug { "MOCK: Executing command: $command" }
        executedCommands.add(command)
        return Response(commandOutput)
    }

    override fun uploadFile(
        local: Path,
        remote: String,
    ) {
        log.debug { "MOCK: Uploading file from ${local.toAbsolutePath()} to $remote" }
        uploadedFiles.add(Pair(local, remote))
    }

    override fun uploadDirectory(
        localDir: File,
        remoteDir: String,
    ) {
        log.debug { "MOCK: Uploading directory from ${localDir.absolutePath} to $remoteDir" }
        uploadedDirectories.add(Pair(localDir, remoteDir))
    }

    override fun downloadFile(
        remote: String,
        local: Path,
    ) {
        log.debug { "MOCK: Downloading file from $remote to ${local.toAbsolutePath()}" }
        downloadedFiles.add(Pair(remote, local))
    }

    override fun downloadDirectory(
        remoteDir: String,
        localDir: File,
        includeFilters: List<String>,
        excludeFilters: List<String>,
    ) {
        log.debug {
            "MOCK: Downloading directory from $remoteDir to ${localDir.absolutePath} with " +
                "includeFilters: $includeFilters, excludeFilters: $excludeFilters"
        }
        downloadedDirectories.add(Pair(remoteDir, localDir))
    }

    override fun getScpClient(): CloseableScpClient {
        return object : CloseableScpClient {
            override fun upload(
                local: Path,
                remote: String,
                vararg options: ScpClient.Option,
            ) {
                // No-op for mock implementation
            }

            override fun upload(
                locals: Array<out Path>,
                remote: String,
                options: Collection<ScpClient.Option>,
            ) {
                // No-op for mock implementation
            }

            override fun upload(
                p0: InputStream?,
                p1: String?,
                p2: Long,
                p3: MutableCollection<PosixFilePermission>?,
                p4: ScpTimestampCommandDetails?,
            ) {
                TODO("Not yet implemented")
            }

            override fun upload(
                locals: Array<out String>,
                remote: String,
                options: Collection<ScpClient.Option>,
            ) {
                // No-op for mock implementation
            }

            override fun getClientSession(): ClientSession {
                TODO("Not yet implemented")
            }

            override fun download(
                p0: String?,
                p1: String?,
                p2: MutableCollection<ScpClient.Option>?,
            ) {
                TODO("Not yet implemented")
            }

            override fun download(
                remote: String,
                local: Path,
                vararg options: ScpClient.Option,
            ) {
                // No-op for mock implementation
            }

            override fun download(
                p0: String?,
                p1: Path?,
                p2: MutableCollection<ScpClient.Option>?,
            ) {
                TODO("Not yet implemented")
            }

            override fun download(
                p0: String?,
                p1: OutputStream?,
            ) {
                TODO("Not yet implemented")
            }

            override fun download(
                p0: Array<out String>?,
                p1: String?,
                p2: MutableCollection<ScpClient.Option>?,
            ) {
                TODO("Not yet implemented")
            }

            override fun download(
                remotes: Array<out String>,
                local: Path,
                options: Collection<ScpClient.Option>,
            ) {
                // No-op for mock implementation
            }

            override fun close() {
                // No-op for mock implementation
            }

            override fun isOpen(): Boolean {
                TODO("Not yet implemented")
            }
        }
    }

    override fun close() {
        log.debug { "MOCK: Stopping SSH client" }
    }

    /**
     * Reset all recorded calls (useful between tests)
     */
    fun reset() {
        executedCommands.clear()
        uploadedFiles.clear()
        uploadedDirectories.clear()
        downloadedFiles.clear()
        downloadedDirectories.clear()
        commandOutput = ""
    }
}
