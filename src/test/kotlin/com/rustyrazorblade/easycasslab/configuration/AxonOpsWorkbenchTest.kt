package com.rustyrazorblade.easycasslab.configuration

import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class AxonOpsWorkbenchTest {
    
    @Test
    fun `test create method with Host and User`() {
        // Create a mock Host
        val host = Host(
            public = "54.123.45.67",
            private = "10.0.1.100",
            alias = "cassandra0",
            availabilityZone = "us-west-2a"
        )
        
        // Create a mock User configuration
        val userConfig = User(
            email = "test@example.com",
            region = "us-west-2",
            keyName = "test-key",
            sshKeyPath = "/home/user/.easy-cass-lab/secret.pem",
            awsProfile = "",
            awsAccessKey = "ACCESS_KEY",
            awsSecret = "SECRET_KEY",
            axonOpsOrg = "",
            axonOpsKey = ""
        )
        
        // Create configuration using the helper method
        val config = AxonOpsWorkbenchConfig.create(
            host = host,
            userConfig = userConfig,
            clusterName = "test-cluster"
        )
        
        // Verify configuration is created correctly
        assertEquals("test-cluster", config.basic.name)
        assertEquals("datacenter1", config.basic.datacenter)
        assertEquals("10.0.1.100", config.basic.hostname) // Should use private IP
        assertEquals("9042", config.basic.port)
        
        // Verify SSH configuration
        assertEquals("54.123.45.67", config.ssh.host) // Should use public IP
        assertEquals("22", config.ssh.port)
        assertEquals("ubuntu", config.ssh.username)
        assertEquals("/home/user/.easy-cass-lab/secret.pem", config.ssh.privatekey)
        assertEquals("10.0.1.100", config.ssh.destaddr) // Should use private IP
        assertEquals("9042", config.ssh.destport)
        
        // Verify empty auth and SSL configs
        assertEquals("", config.auth.username)
        assertEquals("", config.auth.password)
        assertEquals("", config.ssl.ssl)
    }
    
    @Test
    fun `test writeToFile`(@TempDir tempDir: File) {
        // Create a mock Host and User
        val host = Host(
            public = "54.123.45.67",
            private = "10.0.1.100",
            alias = "cassandra0",
            availabilityZone = "us-west-2a"
        )
        
        val userConfig = User(
            email = "test@example.com",
            region = "us-west-2",
            keyName = "test-key",
            sshKeyPath = "/path/to/key.pem",
            awsProfile = "",
            awsAccessKey = "ACCESS_KEY",
            awsSecret = "SECRET_KEY",
            axonOpsOrg = "",
            axonOpsKey = ""
        )
        
        // Create configuration using the helper method
        val config = AxonOpsWorkbenchConfig.create(host, userConfig, "test-write")
        
        // Write to file
        val outputFile = File(tempDir, "axonops-workbench.json")
        AxonOpsWorkbenchConfig.writeToFile(config, outputFile)
        
        // Verify file exists and can be read back
        assertTrue(outputFile.exists())
        val parsedConfig = AxonOpsWorkbenchConfig.json.decodeFromString<AxonOpsWorkbenchConfig>(outputFile.readText())
        assertEquals(config, parsedConfig)
    }
}