package com.rustyrazorblade.easydblab.commands.cassandra.stress

import com.rustyrazorblade.easydblab.BaseKoinTest
import com.rustyrazorblade.easydblab.configuration.ClusterHost
import com.rustyrazorblade.easydblab.configuration.ClusterState
import com.rustyrazorblade.easydblab.configuration.ClusterStateManager
import com.rustyrazorblade.easydblab.configuration.ServerType
import com.rustyrazorblade.easydblab.services.StressJobService
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.module.Module
import org.koin.dsl.module
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File

/**
 * Test suite for StressStart command.
 *
 * These tests verify stress job creation via StressJobService.
 */
class StressStartTest : BaseKoinTest() {
    private lateinit var mockStressJobService: StressJobService
    private lateinit var mockClusterStateManager: ClusterStateManager

    private val testControlHost =
        ClusterHost(
            publicIp = "54.123.45.67",
            privateIp = "10.0.1.5",
            alias = "control0",
            availabilityZone = "us-west-2a",
            instanceId = "i-test123",
        )

    private val testCassandraHost =
        ClusterHost(
            publicIp = "54.123.45.68",
            privateIp = "10.0.1.6",
            alias = "cassandra0",
            availabilityZone = "us-west-2a",
            instanceId = "i-test124",
        )

    private val testCassandraHost2 =
        ClusterHost(
            publicIp = "54.123.45.69",
            privateIp = "10.0.1.7",
            alias = "cassandra1",
            availabilityZone = "us-west-2b",
            instanceId = "i-test125",
        )

    override fun additionalTestModules(): List<Module> =
        listOf(
            module {
                single {
                    mock<StressJobService>().also {
                        mockStressJobService = it
                    }
                }

                single {
                    mock<ClusterStateManager>().also {
                        mockClusterStateManager = it
                    }
                }
            },
        )

    @BeforeEach
    fun setupMocks() {
        mockStressJobService = getKoin().get()
        mockClusterStateManager = getKoin().get()
    }

    @Test
    fun `execute should fail when no control nodes exist`() {
        // Given - cluster state with no control nodes
        val emptyState =
            ClusterState(
                name = "test-cluster",
                versions = mutableMapOf(),
                hosts = mutableMapOf(),
            )

        whenever(mockClusterStateManager.load()).thenReturn(emptyState)

        val command = StressStart()

        // When/Then
        assertThatThrownBy { command.execute() }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("No control nodes found")
    }

    @Test
    fun `execute should fail when no Cassandra nodes exist`() {
        // Given - cluster state with control node but no Cassandra nodes
        val stateWithControlOnly =
            ClusterState(
                name = "test-cluster",
                versions = mutableMapOf(),
                hosts =
                    mutableMapOf(
                        ServerType.Control to listOf(testControlHost),
                    ),
            )

        whenever(mockClusterStateManager.load()).thenReturn(stateWithControlOnly)

        val command = StressStart()

        // When/Then
        assertThatThrownBy { command.execute() }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("No Cassandra nodes found")
    }

    @Test
    fun `execute should start job with default KeyValue workload when no args specified`() {
        // Given - cluster state with control node and Cassandra nodes
        val stateWithNodes =
            ClusterState(
                name = "test-cluster",
                versions = mutableMapOf(),
                hosts =
                    mutableMapOf(
                        ServerType.Control to listOf(testControlHost),
                        ServerType.Cassandra to listOf(testCassandraHost, testCassandraHost2),
                    ),
            )

        whenever(mockClusterStateManager.load()).thenReturn(stateWithNodes)
        whenever(mockStressJobService.startJob(any(), any(), any(), any(), any(), anyOrNull()))
            .thenReturn(Result.success("job-created"))

        val command = StressStart()

        // When
        command.execute()

        // Then - verify startJob was called with KeyValue workload and contact points
        verify(mockStressJobService).startJob(
            controlHost = eq(testControlHost),
            jobName = argThat { startsWith("stress-") },
            image = any(),
            args =
                argThat { args ->
                    args.contains("run") && args.contains("KeyValue") && args.contains("10.0.1.6,10.0.1.7")
                },
            contactPoints = eq("10.0.1.6,10.0.1.7"),
            profileConfig = isNull(),
        )
    }

    @Test
    fun `execute should pass through workload args directly`() {
        // Given - cluster state with nodes
        val stateWithNodes =
            ClusterState(
                name = "test-cluster",
                versions = mutableMapOf(),
                hosts =
                    mutableMapOf(
                        ServerType.Control to listOf(testControlHost),
                        ServerType.Cassandra to listOf(testCassandraHost),
                    ),
            )

        whenever(mockClusterStateManager.load()).thenReturn(stateWithNodes)
        whenever(mockStressJobService.startJob(any(), any(), any(), any(), any(), anyOrNull()))
            .thenReturn(Result.success("job-created"))

        val command = StressStart()
        command.stressArgs = listOf("BasicTimeSeries", "-d", "1h", "--threads", "100")

        // When
        command.execute()

        // Then - verify startJob was called with passthrough args
        verify(mockStressJobService).startJob(
            controlHost = eq(testControlHost),
            jobName = any(),
            image = any(),
            args =
                argThat { args ->
                    args.contains("run") &&
                        args.contains("BasicTimeSeries") &&
                        args.contains("-d") &&
                        args.contains("1h") &&
                        args.contains("--threads") &&
                        args.contains("100")
                },
            contactPoints = any(),
            profileConfig = isNull(),
        )
    }

    @Test
    fun `execute should start job with profile when provided`() {
        // Given - cluster state with nodes and a profile file
        val stateWithNodes =
            ClusterState(
                name = "test-cluster",
                versions = mutableMapOf(),
                hosts =
                    mutableMapOf(
                        ServerType.Control to listOf(testControlHost),
                        ServerType.Cassandra to listOf(testCassandraHost),
                    ),
            )

        val profileFile = File(tempDir, "my-workload.yaml")
        profileFile.writeText("keyspace: test\ntable: data")

        whenever(mockClusterStateManager.load()).thenReturn(stateWithNodes)
        whenever(mockStressJobService.startJob(any(), any(), any(), any(), any(), anyOrNull()))
            .thenReturn(Result.success("job-created"))

        val command = StressStart()
        command.profilePath = profileFile.toPath()

        // When
        command.execute()

        // Then - verify startJob was called with profile config
        verify(mockStressJobService).startJob(
            controlHost = eq(testControlHost),
            jobName = any(),
            image = any(),
            args = any(),
            contactPoints = any(),
            profileConfig =
                argThat { config ->
                    config != null && config.first == "my-workload.yaml" && config.second.contains("keyspace: test")
                },
        )
    }

    @Test
    fun `execute should fail when startJob fails`() {
        // Given - cluster state with nodes
        val stateWithNodes =
            ClusterState(
                name = "test-cluster",
                versions = mutableMapOf(),
                hosts =
                    mutableMapOf(
                        ServerType.Control to listOf(testControlHost),
                        ServerType.Cassandra to listOf(testCassandraHost),
                    ),
            )

        whenever(mockClusterStateManager.load()).thenReturn(stateWithNodes)
        whenever(mockStressJobService.startJob(any(), any(), any(), any(), any(), anyOrNull()))
            .thenReturn(Result.failure(RuntimeException("Job creation failed")))

        val command = StressStart()

        // When/Then
        assertThatThrownBy { command.execute() }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("Job creation failed")
    }

    @Test
    fun `execute should fail when profile file does not exist`() {
        // Given - cluster state with nodes
        val stateWithNodes =
            ClusterState(
                name = "test-cluster",
                versions = mutableMapOf(),
                hosts =
                    mutableMapOf(
                        ServerType.Control to listOf(testControlHost),
                        ServerType.Cassandra to listOf(testCassandraHost),
                    ),
            )

        whenever(mockClusterStateManager.load()).thenReturn(stateWithNodes)

        val command = StressStart()
        command.profilePath = File(tempDir, "nonexistent.yaml").toPath()

        // When/Then
        assertThatThrownBy { command.execute() }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("not found")
    }

    @Test
    fun `execute should use custom job name when provided`() {
        // Given - cluster state with nodes
        val stateWithNodes =
            ClusterState(
                name = "test-cluster",
                versions = mutableMapOf(),
                hosts =
                    mutableMapOf(
                        ServerType.Control to listOf(testControlHost),
                        ServerType.Cassandra to listOf(testCassandraHost),
                    ),
            )

        whenever(mockClusterStateManager.load()).thenReturn(stateWithNodes)
        whenever(mockStressJobService.startJob(any(), any(), any(), any(), any(), anyOrNull()))
            .thenReturn(Result.success("job-created"))

        val command = StressStart()
        command.jobName = "my-test"

        // When
        command.execute()

        // Then - verify job name contains the custom name
        verify(mockStressJobService).startJob(
            controlHost = eq(testControlHost),
            jobName = argThat { name -> name.contains("stress-my-test-") },
            image = any(),
            args = any(),
            contactPoints = any(),
            profileConfig = isNull(),
        )
    }
}
