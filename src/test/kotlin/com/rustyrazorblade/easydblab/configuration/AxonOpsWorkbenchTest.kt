package com.rustyrazorblade.easydblab.configuration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class AxonOpsWorkbenchTest {
    @Test
    fun `test create method with Host and sshKeyPath`() {
        // Create a mock Host
        val host =
            Host(
                public = "54.123.45.67",
                private = "10.0.1.100",
                alias = "db0",
                availabilityZone = "us-west-2a",
            )

        val sshKeyPath = "/home/user/.easy-db-lab/secret.pem"

        // Create configuration using the helper method
        val config =
            AxonOpsWorkbenchConfig.create(
                host = host,
                sshKeyPath = sshKeyPath,
                clusterName = "test-cluster",
            )

        // Verify configuration is created correctly
        assertThat(config.basic.name).isEqualTo("test-cluster")
        assertThat(config.basic.datacenter).isEqualTo("datacenter1")
        assertThat(config.basic.hostname).isEqualTo("10.0.1.100") // Should use private IP
        assertThat(config.basic.port).isEqualTo("9042")

        // Verify SSH configuration
        assertThat(config.ssh.host).isEqualTo("54.123.45.67") // Should use public IP
        assertThat(config.ssh.port).isEqualTo("22")
        assertThat(config.ssh.username).isEqualTo("ubuntu")
        assertThat(config.ssh.privatekey).isEqualTo("/home/user/.easy-db-lab/secret.pem")
        assertThat(config.ssh.destaddr).isEqualTo("10.0.1.100") // Should use private IP
        assertThat(config.ssh.destport).isEqualTo("9042")

        // Verify empty auth and SSL configs
        assertThat(config.auth.username).isEmpty()
        assertThat(config.auth.password).isEmpty()
        assertThat(config.ssl.ssl).isEmpty()
    }

    @Test
    fun `test writeToFile`(
        @TempDir tempDir: File,
    ) {
        // Create a mock Host
        val host =
            Host(
                public = "54.123.45.67",
                private = "10.0.1.100",
                alias = "db0",
                availabilityZone = "us-west-2a",
            )

        val sshKeyPath = "/path/to/key.pem"

        // Create configuration using the helper method
        val config = AxonOpsWorkbenchConfig.create(host, sshKeyPath, "test-write")

        // Write to file
        val outputFile = File(tempDir, "axonops-workbench.json")
        AxonOpsWorkbenchConfig.writeToFile(config, outputFile)

        // Verify file exists and can be read back
        assertThat(outputFile).exists()
        val parsedConfig = AxonOpsWorkbenchConfig.json.decodeFromString<AxonOpsWorkbenchConfig>(outputFile.readText())
        assertThat(parsedConfig).isEqualTo(config)
    }
}
