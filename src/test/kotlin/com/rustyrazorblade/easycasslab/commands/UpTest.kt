package com.rustyrazorblade.easycasslab.commands

import com.rustyrazorblade.easycasslab.BaseKoinTest
import com.rustyrazorblade.easycasslab.configuration.ClusterState
import com.rustyrazorblade.easycasslab.configuration.ClusterStateManager
import com.rustyrazorblade.easycasslab.configuration.Host
import com.rustyrazorblade.easycasslab.configuration.InitConfig
import com.rustyrazorblade.easycasslab.configuration.ServerType
import com.rustyrazorblade.easycasslab.configuration.TFState
import com.rustyrazorblade.easycasslab.configuration.User
import com.rustyrazorblade.easycasslab.di.TFStateProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File
import java.io.InputStream

/**
 * Tests for the Up command's environment variable generation functionality.
 *
 * Focuses on verifying that the environment.sh file is generated correctly
 * with all required environment variables for stress testing.
 */
class UpTest : BaseKoinTest() {
    private lateinit var environmentFile: File
    private lateinit var clusterStateFile: File

    @BeforeEach
    fun setupTestEnvironmentFile() {
        environmentFile = File("environment.sh")
        clusterStateFile = File("state.json")

        // Ensure files don't exist before test
        if (environmentFile.exists()) {
            environmentFile.delete()
        }
        if (clusterStateFile.exists()) {
            clusterStateFile.delete()
        }
    }

    @AfterEach
    fun cleanupTestEnvironmentFile() {
        if (environmentFile.exists()) {
            environmentFile.delete()
        }
        if (clusterStateFile.exists()) {
            clusterStateFile.delete()
        }
    }

    @Test
    fun `writeStressEnvironmentVariables creates environment file with datacenter variable`() {
        // Arrange - Create a test module with mocked TFState
        val testRegion = "us-west-2"
        val cassandraHost =
            Host(
                public = "3.3.3.3",
                private = "10.0.1.5",
                alias = "cassandra0",
                availabilityZone = "us-west-2a",
            )

        val mockTFState = mock<TFState>()
        whenever(mockTFState.getHosts(ServerType.Cassandra)).thenReturn(listOf(cassandraHost))

        val mockUserConfig = mock<User>()
        whenever(mockUserConfig.region).thenReturn(testRegion)

        // Rebuild Koin with mocked TFState
        tearDownKoin()
        val customModules =
            listOf(
                module {
                    single<TFStateProvider> {
                        object : TFStateProvider {
                            override fun parseFromFile(file: File): TFState = mockTFState

                            override fun parseFromStream(stream: InputStream): TFState = mockTFState

                            override fun getDefault(): TFState = mockTFState
                        }
                    }
                    single { mockTFState }
                    single { mockUserConfig }
                },
            )
        setupKoin()
        org.koin.core.context
            .loadKoinModules(customModules)

        // Create Up command instance
        val upCommand = Up(context)

        // Use reflection to call the private method for testing
        val writeMethod =
            Up::class.java.getDeclaredMethod("writeStressEnvironmentVariables").apply {
                isAccessible = true
            }

        // Act
        writeMethod.invoke(upCommand)

        // Assert
        assertThat(environmentFile).exists()
        val content = environmentFile.readText()

        // Verify file header
        assertThat(content).contains("#!/usr/bin/env bash")

        // Verify all three required environment variables are present
        assertThat(content).contains("export CASSANDRA_EASY_STRESS_CASSANDRA_HOST=${cassandraHost.private}")
        assertThat(content).contains("export CASSANDRA_EASY_STRESS_PROM_PORT=0")
        assertThat(content).contains("export CASSANDRA_EASY_STRESS_DEFAULT_DC=$testRegion")
    }

    @Test
    fun `writeStressEnvironmentVariables uses ClusterState datacenter when available`() {
        // Arrange
        val clusterStateRegion = "eu-west-1"
        val userConfigRegion = "us-east-1"
        val cassandraHost =
            Host(
                public = "4.4.4.4",
                private = "10.0.1.10",
                alias = "cassandra0",
                availabilityZone = "eu-west-1a",
            )

        // Create ClusterState with region
        val initConfig = InitConfig(region = clusterStateRegion)
        val clusterState =
            ClusterState(
                name = "test-cluster",
                versions = mutableMapOf<String, String>(),
                initConfig = initConfig,
            )

        // Create a mock ClusterStateManager that returns our clusterState
        val mockClusterStateManager = mock<ClusterStateManager>()
        whenever(mockClusterStateManager.load()).thenReturn(clusterState)

        val mockTFState = mock<TFState>()
        whenever(mockTFState.getHosts(ServerType.Cassandra)).thenReturn(listOf(cassandraHost))

        val mockUserConfig = mock<User>()
        whenever(mockUserConfig.region).thenReturn(userConfigRegion)

        // Rebuild Koin with mocked TFState and ClusterStateManager
        tearDownKoin()
        val customModules =
            listOf(
                module {
                    single<TFStateProvider> {
                        object : TFStateProvider {
                            override fun parseFromFile(file: File): TFState = mockTFState

                            override fun parseFromStream(stream: InputStream): TFState = mockTFState

                            override fun getDefault(): TFState = mockTFState
                        }
                    }
                    single { mockTFState }
                    single { mockUserConfig }
                    single { mockClusterStateManager }
                },
            )
        setupKoin()
        org.koin.core.context
            .loadKoinModules(customModules)

        // Create Up command instance
        val upCommand = Up(context)

        // Use reflection to call the private method
        val writeMethod =
            Up::class.java.getDeclaredMethod("writeStressEnvironmentVariables").apply {
                isAccessible = true
            }

        // Act
        writeMethod.invoke(upCommand)

        // Assert
        assertThat(environmentFile).exists()
        val content = environmentFile.readText()

        // Should use ClusterState region, not userConfig region
        assertThat(content).contains("export CASSANDRA_EASY_STRESS_DEFAULT_DC=$clusterStateRegion")
        assertThat(content).doesNotContain("export CASSANDRA_EASY_STRESS_DEFAULT_DC=$userConfigRegion")
    }

    @Test
    fun `writeStressEnvironmentVariables falls back to userConfig region when ClusterState unavailable`() {
        // Arrange
        val userConfigRegion = "ap-south-1"
        val cassandraHost =
            Host(
                public = "5.5.5.5",
                private = "10.0.1.15",
                alias = "cassandra0",
                availabilityZone = "ap-south-1a",
            )

        val mockTFState = mock<TFState>()
        whenever(mockTFState.getHosts(ServerType.Cassandra)).thenReturn(listOf(cassandraHost))

        val mockUserConfig = mock<User>()
        whenever(mockUserConfig.region).thenReturn(userConfigRegion)

        // Rebuild Koin with mocked TFState
        tearDownKoin()
        val customModules =
            listOf(
                module {
                    single<TFStateProvider> {
                        object : TFStateProvider {
                            override fun parseFromFile(file: File): TFState = mockTFState

                            override fun parseFromStream(stream: InputStream): TFState = mockTFState

                            override fun getDefault(): TFState = mockTFState
                        }
                    }
                    single { mockTFState }
                    single { mockUserConfig }
                },
            )
        setupKoin()
        org.koin.core.context
            .loadKoinModules(customModules)

        // Create Up command instance
        val upCommand = Up(context)

        // Use reflection to call the private method
        val writeMethod =
            Up::class.java.getDeclaredMethod("writeStressEnvironmentVariables").apply {
                isAccessible = true
            }

        // Act
        writeMethod.invoke(upCommand)

        // Assert
        assertThat(environmentFile).exists()
        val content = environmentFile.readText()

        // Should fall back to userConfig region
        assertThat(content).contains("export CASSANDRA_EASY_STRESS_DEFAULT_DC=$userConfigRegion")
    }
}
