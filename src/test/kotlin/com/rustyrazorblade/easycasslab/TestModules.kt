package com.rustyrazorblade.easycasslab

import com.rustyrazorblade.easycasslab.configuration.Host
import com.rustyrazorblade.easycasslab.providers.ssh.DefaultSSHConfiguration
import com.rustyrazorblade.easycasslab.providers.ssh.RemoteOperationsService
import com.rustyrazorblade.easycasslab.providers.ssh.SSHConfiguration
import com.rustyrazorblade.easycasslab.providers.ssh.SSHConnectionProvider
import com.rustyrazorblade.easycasslab.ssh.ISSHClient
import com.rustyrazorblade.easycasslab.ssh.MockSSHClient
import com.rustyrazorblade.easycasslab.ssh.Response
import org.koin.dsl.module
import java.io.File
import java.nio.file.Path

/**
 * Test modules for Koin dependency injection in tests.
 */
object TestModules {
    
    /**
     * Creates a test SSH module with mock implementations.
     */
    fun testSSHModule() = module {
        // Mock SSH configuration
        single<SSHConfiguration> {
            DefaultSSHConfiguration(keyPath = "test")
        }
        
        // Mock SSH connection provider
        single<SSHConnectionProvider> {
            object : SSHConnectionProvider {
                private val mockClient = MockSSHClient()
                
                override fun getConnection(host: Host): ISSHClient = mockClient
                override fun stop() {}
            }
        }
        
        // Mock remote operations service
        factory<RemoteOperationsService> {
            object : RemoteOperationsService {
                override fun executeRemotely(
                    host: Host,
                    command: String,
                    output: Boolean,
                    secret: Boolean
                ): Response = Response("")
                
                override fun upload(host: Host, local: Path, remote: String) {}
                override fun uploadDirectory(host: Host, localDir: File, remoteDir: String) {}
                override fun uploadDirectory(host: Host, version: Version) {}
                override fun download(host: Host, remote: String, local: Path) {}
                override fun downloadDirectory(
                    host: Host,
                    remoteDir: String,
                    localDir: File,
                    includeFilters: List<String>,
                    excludeFilters: List<String>
                ) {}
                
                override fun getRemoteVersion(host: Host, inputVersion: String): Version {
                    return if (inputVersion == "current") {
                        Version("/usr/local/cassandra/5.0")
                    } else {
                        Version.fromString(inputVersion)
                    }
                }
            }
        }
    }
}