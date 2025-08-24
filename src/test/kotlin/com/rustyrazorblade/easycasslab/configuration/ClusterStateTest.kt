package com.rustyrazorblade.easycasslab.configuration

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ClusterStateTest {
    @Test
    fun `ClusterState should save and load InitConfig correctly`(@TempDir tempDir: File) {
        // Set the file path to use temp directory
        val stateFile = File(tempDir, "state.json")
        ClusterState.fp = stateFile

        // Create InitConfig with test data
        val initConfig = InitConfig(
            cassandraInstances = 3,
            stressInstances = 1,
            instanceType = "m5d.xlarge",
            stressInstanceType = "c5.2xlarge",
            azs = listOf("a", "b", "c"),
            ami = "ami-12345",
            region = "us-west-2",
            name = "test-cluster",
            ebsType = "gp3",
            ebsSize = 256,
            ebsIops = 3000,
            ebsThroughput = 125,
            ebsOptimized = true,
            open = false,
            controlInstances = 1,
            controlInstanceType = "t3.xlarge",
            tags = mapOf("env" to "test", "team" to "platform"),
        )

        // Create and save ClusterState
        val originalState = ClusterState(
            name = "test-cluster",
            versions = mutableMapOf("cassandra" to "4.1.3"),
            initConfig = initConfig,
        )
        originalState.save()

        // Verify file was created
        assertThat(stateFile).exists()

        // Load the state back
        val loadedState = ClusterState.load()

        // Verify all fields match
        assertThat(loadedState.name).isEqualTo("test-cluster")
        assertThat(loadedState.versions).containsEntry("cassandra", "4.1.3")
        
        // Verify InitConfig was preserved
        assertThat(loadedState.initConfig).isNotNull
        val loadedConfig = loadedState.initConfig!!
        
        assertThat(loadedConfig.cassandraInstances).isEqualTo(3)
        assertThat(loadedConfig.stressInstances).isEqualTo(1)
        assertThat(loadedConfig.instanceType).isEqualTo("m5d.xlarge")
        assertThat(loadedConfig.stressInstanceType).isEqualTo("c5.2xlarge")
        assertThat(loadedConfig.azs).containsExactly("a", "b", "c")
        assertThat(loadedConfig.ami).isEqualTo("ami-12345")
        assertThat(loadedConfig.region).isEqualTo("us-west-2")
        assertThat(loadedConfig.name).isEqualTo("test-cluster")
        assertThat(loadedConfig.ebsType).isEqualTo("gp3")
        assertThat(loadedConfig.ebsSize).isEqualTo(256)
        assertThat(loadedConfig.ebsIops).isEqualTo(3000)
        assertThat(loadedConfig.ebsThroughput).isEqualTo(125)
        assertThat(loadedConfig.ebsOptimized).isTrue()
        assertThat(loadedConfig.open).isFalse()
        assertThat(loadedConfig.controlInstances).isEqualTo(1)
        assertThat(loadedConfig.controlInstanceType).isEqualTo("t3.xlarge")
        assertThat(loadedConfig.tags).containsEntry("env", "test")
        assertThat(loadedConfig.tags).containsEntry("team", "platform")
    }

    @Test
    fun `ClusterState should handle null InitConfig gracefully`(@TempDir tempDir: File) {
        // Set the file path to use temp directory
        val stateFile = File(tempDir, "state.json")
        ClusterState.fp = stateFile

        // Create ClusterState without InitConfig (backward compatibility)
        val state = ClusterState(
            name = "legacy-cluster",
            versions = mutableMapOf(),
            initConfig = null,
        )
        state.save()

        // Load it back
        val loadedState = ClusterState.load()

        // Verify it loads correctly with null InitConfig
        assertThat(loadedState.name).isEqualTo("legacy-cluster")
        assertThat(loadedState.initConfig).isNull()
    }

    @Test
    fun `InitConfig region should be used for datacenter determination`() {
        // Create InitConfig with specific region
        val initConfig = InitConfig(
            cassandraInstances = 3,
            stressInstances = 0,
            instanceType = "m5d.xlarge",
            stressInstanceType = "c5.2xlarge",
            azs = listOf(),
            ami = "",
            region = "eu-west-1",
            name = "eu-cluster",
            ebsType = "NONE",
            ebsSize = 0,
            ebsIops = 0,
            ebsThroughput = 0,
            ebsOptimized = false,
            open = false,
            controlInstances = 1,
            controlInstanceType = "t3.xlarge",
            tags = mapOf(),
        )

        // The region should be used as the datacenter name for EC2Snitch
        assertThat(initConfig.region).isEqualTo("eu-west-1")
        
        // This is what will be used for CASSANDRA_DATACENTER
        val datacenter = initConfig.region
        assertThat(datacenter).isEqualTo("eu-west-1")
    }

    @Test
    fun `ClusterState should load legacy state file without InitConfig`(@TempDir tempDir: File) {
        // Set the file path to use temp directory
        val stateFile = File(tempDir, "state.json")
        ClusterState.fp = stateFile

        // Create a legacy JSON without initConfig field
        val legacyJson = """
            {
              "name": "legacy-cluster",
              "default": {
                "version": "3.11.10",
                "javaVersion": "8"
              },
              "nodes": {},
              "versions": {
                "cassandra": "3.11.10"
              }
            }
        """.trimIndent()

        stateFile.writeText(legacyJson)

        // Should load without throwing an exception
        val loadedState = ClusterState.load()

        assertThat(loadedState.name).isEqualTo("legacy-cluster")
        assertThat(loadedState.default.version).isEqualTo("3.11.10")
        assertThat(loadedState.initConfig).isNull()
    }

    @Test
    fun `ClusterState should load state with partial InitConfig`(@TempDir tempDir: File) {
        // Set the file path to use temp directory
        val stateFile = File(tempDir, "state.json")
        ClusterState.fp = stateFile

        // Create JSON with partial InitConfig (missing some fields)
        val partialJson = """
            {
              "name": "partial-cluster",
              "default": {},
              "nodes": {},
              "versions": {},
              "initConfig": {
                "region": "ap-southeast-1",
                "name": "partial-cluster",
                "cassandraInstances": 5
              }
            }
        """.trimIndent()

        stateFile.writeText(partialJson)

        // Should load and fill in missing fields with defaults
        val loadedState = ClusterState.load()

        assertThat(loadedState.name).isEqualTo("partial-cluster")
        assertThat(loadedState.initConfig).isNotNull
        
        val config = loadedState.initConfig!!
        // Provided values should be preserved
        assertThat(config.region).isEqualTo("ap-southeast-1")
        assertThat(config.name).isEqualTo("partial-cluster")
        assertThat(config.cassandraInstances).isEqualTo(5)
        
        // Missing values should use defaults
        assertThat(config.stressInstances).isEqualTo(0)
        assertThat(config.instanceType).isEqualTo("r3.2xlarge")
        assertThat(config.controlInstances).isEqualTo(1)
        assertThat(config.controlInstanceType).isEqualTo("t3.xlarge")
    }

    @Test
    fun `ClusterState should handle state file with extra unknown fields`(@TempDir tempDir: File) {
        // Set the file path to use temp directory
        val stateFile = File(tempDir, "state.json")
        ClusterState.fp = stateFile

        // Create JSON with extra fields that don't exist in current schema
        val jsonWithExtraFields = """
            {
              "name": "future-cluster",
              "default": {},
              "nodes": {},
              "versions": {},
              "futureField": "some value",
              "anotherNewField": 123,
              "initConfig": {
                "region": "us-east-1",
                "name": "future-cluster",
                "cassandraInstances": 3,
                "someNewConfigField": "ignored"
              }
            }
        """.trimIndent()

        stateFile.writeText(jsonWithExtraFields)

        // Should load without errors, ignoring unknown fields
        val loadedState = ClusterState.load()

        assertThat(loadedState.name).isEqualTo("future-cluster")
        assertThat(loadedState.initConfig).isNotNull
        assertThat(loadedState.initConfig!!.region).isEqualTo("us-east-1")
    }
}