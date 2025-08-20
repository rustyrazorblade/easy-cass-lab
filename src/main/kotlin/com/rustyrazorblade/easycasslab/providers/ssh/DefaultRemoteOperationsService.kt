package com.rustyrazorblade.easycasslab.providers.ssh

import com.rustyrazorblade.easycasslab.Version
import com.rustyrazorblade.easycasslab.configuration.Host
import com.rustyrazorblade.easycasslab.ssh.Response
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.File
import java.nio.file.Path

/**
 * Default implementation of RemoteOperationsService.
 * Provides high-level SSH operations using an SSHConnectionProvider.
 * 
 * @param connectionProvider Provider for SSH connections
 */
class DefaultRemoteOperationsService(
    private val connectionProvider: SSHConnectionProvider
) : RemoteOperationsService {
    
    companion object {
        private val log = KotlinLogging.logger {}
    }
    
    override fun executeRemotely(
        host: Host,
        command: String,
        output: Boolean,
        secret: Boolean
    ): Response {
        log.debug { "Executing command on ${host.alias}: ${if (secret) "[REDACTED]" else command}" }
        return connectionProvider.getConnection(host).executeRemoteCommand(command, output, secret)
    }
    
    override fun upload(
        host: Host,
        local: Path,
        remote: String
    ) {
        log.info { "Uploading $local to ${host.alias}:$remote" }
        connectionProvider.getConnection(host).uploadFile(local, remote)
    }
    
    override fun uploadDirectory(
        host: Host,
        localDir: File,
        remoteDir: String
    ) {
        log.info { "Uploading directory $localDir to ${host.alias}:$remoteDir" }
        println("Uploading directory $localDir to $remoteDir")
        connectionProvider.getConnection(host).uploadDirectory(localDir, remoteDir)
    }
    
    override fun uploadDirectory(
        host: Host,
        version: Version
    ) {
        uploadDirectory(host, version.file, version.conf)
    }
    
    override fun download(
        host: Host,
        remote: String,
        local: Path
    ) {
        log.info { "Downloading ${host.alias}:$remote to $local" }
        connectionProvider.getConnection(host).downloadFile(remote, local)
    }
    
    override fun downloadDirectory(
        host: Host,
        remoteDir: String,
        localDir: File,
        includeFilters: List<String>,
        excludeFilters: List<String>
    ) {
        log.info { 
            "Downloading directory ${host.alias}:$remoteDir to $localDir " +
            "(include: $includeFilters, exclude: $excludeFilters)"
        }
        connectionProvider.getConnection(host).downloadDirectory(
            remoteDir, 
            localDir, 
            includeFilters, 
            excludeFilters
        )
    }
    
    override fun getRemoteVersion(
        host: Host,
        inputVersion: String
    ): Version {
        return if (inputVersion == "current") {
            val path = executeRemotely(host, "readlink -f /usr/local/cassandra/current").text.trim()
            Version(path)
        } else {
            Version.fromString(inputVersion)
        }
    }
}