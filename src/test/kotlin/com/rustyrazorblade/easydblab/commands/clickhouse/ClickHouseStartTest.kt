package com.rustyrazorblade.easydblab.commands.clickhouse

import com.rustyrazorblade.easydblab.BaseKoinTest
import com.rustyrazorblade.easydblab.Constants
import com.rustyrazorblade.easydblab.configuration.ClusterHost
import com.rustyrazorblade.easydblab.configuration.ClusterState
import com.rustyrazorblade.easydblab.configuration.ClusterStateManager
import com.rustyrazorblade.easydblab.configuration.ServerType
import com.rustyrazorblade.easydblab.services.K8sService
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.module.Module
import org.koin.dsl.module
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.file.Path

/**
 * Test suite for ClickHouseStart command following TDD principles.
 *
 * These tests verify ClickHouse cluster deployment including
 * manifest application, storage configuration, and K8sService integration.
 */
class ClickHouseStartTest : BaseKoinTest() {
    private lateinit var mockK8sService: K8sService
    private lateinit var mockClusterStateManager: ClusterStateManager

    private val testControlHost =
        ClusterHost(
            publicIp = "54.123.45.67",
            privateIp = "10.0.1.5",
            alias = "control0",
            availabilityZone = "us-west-2a",
            instanceId = "i-test123",
        )

    private val testDbHost =
        ClusterHost(
            publicIp = "54.123.45.68",
            privateIp = "10.0.1.6",
            alias = "db0",
            availabilityZone = "us-west-2a",
            instanceId = "i-test124",
        )

    private val testDbHost2 =
        ClusterHost(
            publicIp = "54.123.45.69",
            privateIp = "10.0.1.7",
            alias = "db1",
            availabilityZone = "us-west-2b",
            instanceId = "i-test125",
        )

    private val testDbHost3 =
        ClusterHost(
            publicIp = "54.123.45.70",
            privateIp = "10.0.1.8",
            alias = "db2",
            availabilityZone = "us-west-2c",
            instanceId = "i-test126",
        )

    override fun additionalTestModules(): List<Module> =
        listOf(
            module {
                single {
                    mock<K8sService>().also {
                        mockK8sService = it
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
        mockK8sService = getKoin().get()
        mockClusterStateManager = getKoin().get()

        // Default mock for scaleStatefulSet - used by most tests
        whenever(mockK8sService.scaleStatefulSet(any(), any(), any(), any())).thenReturn(Result.success(Unit))
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

        val command = ClickHouseStart()

        // When/Then
        assertThatThrownBy { command.execute() }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("No control nodes found")
    }

    @Test
    fun `execute should apply manifests and create S3 secret when bucket configured`() {
        // Given - cluster state with control node, db nodes, and S3 bucket
        val stateWithControlAndDb =
            ClusterState(
                name = "test-cluster",
                versions = mutableMapOf(),
                hosts =
                    mutableMapOf(
                        ServerType.Control to listOf(testControlHost),
                        ServerType.Cassandra to listOf(testDbHost, testDbHost2, testDbHost3),
                    ),
                s3Bucket = "test-bucket",
            )

        whenever(mockClusterStateManager.load()).thenReturn(stateWithControlAndDb)
        whenever(mockK8sService.applyManifests(any(), any())).thenReturn(Result.success(Unit))
        whenever(
            mockK8sService.createClickHouseS3Secret(
                any(),
                eq(Constants.ClickHouse.NAMESPACE),
                eq("us-west-2"),
                eq("test-bucket"),
            ),
        ).thenReturn(Result.success(Unit))
        whenever(mockK8sService.waitForPodsReady(any(), any(), eq(Constants.ClickHouse.NAMESPACE)))
            .thenReturn(Result.success(Unit))

        val command = ClickHouseStart()

        // When
        command.execute()

        // Then - verify manifests are applied from the clickhouse directory
        verify(mockK8sService).applyManifests(eq(testControlHost), eq(Path.of("k8s/clickhouse")))

        // Verify S3 secret is created when bucket is configured
        verify(mockK8sService).createClickHouseS3Secret(
            eq(testControlHost),
            eq(Constants.ClickHouse.NAMESPACE),
            eq("us-west-2"),
            eq("test-bucket"),
        )

        verify(mockK8sService).waitForPodsReady(eq(testControlHost), any(), eq(Constants.ClickHouse.NAMESPACE))
    }

    @Test
    fun `execute should skip S3 secret creation when bucket not configured`() {
        // Given - cluster state without S3 bucket
        val stateWithControlAndDb =
            ClusterState(
                name = "test-cluster",
                versions = mutableMapOf(),
                hosts =
                    mutableMapOf(
                        ServerType.Control to listOf(testControlHost),
                        ServerType.Cassandra to listOf(testDbHost, testDbHost2, testDbHost3),
                    ),
                s3Bucket = null, // No bucket configured
            )

        whenever(mockClusterStateManager.load()).thenReturn(stateWithControlAndDb)
        whenever(mockK8sService.applyManifests(any(), any())).thenReturn(Result.success(Unit))
        whenever(mockK8sService.waitForPodsReady(any(), any(), eq(Constants.ClickHouse.NAMESPACE)))
            .thenReturn(Result.success(Unit))

        val command = ClickHouseStart()

        // When
        command.execute()

        // Then - S3 secret should not be created
        verify(mockK8sService, never()).createClickHouseS3Secret(any(), any(), any(), any())

        // But manifests should still be applied
        verify(mockK8sService).applyManifests(eq(testControlHost), eq(Path.of("k8s/clickhouse")))
    }

    @Test
    fun `execute should skip waiting when skipWait is true`() {
        // Given - cluster state with control node, db nodes, and S3 bucket
        val stateWithControlAndDb =
            ClusterState(
                name = "test-cluster",
                versions = mutableMapOf(),
                hosts =
                    mutableMapOf(
                        ServerType.Control to listOf(testControlHost),
                        ServerType.Cassandra to listOf(testDbHost, testDbHost2, testDbHost3),
                    ),
                s3Bucket = "test-bucket",
            )

        whenever(mockClusterStateManager.load()).thenReturn(stateWithControlAndDb)
        whenever(mockK8sService.applyManifests(any(), any())).thenReturn(Result.success(Unit))
        whenever(
            mockK8sService.createClickHouseS3Secret(
                any(),
                eq(Constants.ClickHouse.NAMESPACE),
                eq("us-west-2"),
                eq("test-bucket"),
            ),
        ).thenReturn(Result.success(Unit))

        val command = ClickHouseStart()
        command.skipWait = true

        // When
        command.execute()

        // Then
        verify(mockK8sService).applyManifests(eq(testControlHost), eq(Path.of("k8s/clickhouse")))
        verify(mockK8sService, never()).waitForPodsReady(any(), any(), any())
    }

    @Test
    fun `execute should fail when applyManifests fails`() {
        // Given - cluster state with control node, db nodes, and S3 bucket
        val stateWithControlAndDb =
            ClusterState(
                name = "test-cluster",
                versions = mutableMapOf(),
                hosts =
                    mutableMapOf(
                        ServerType.Control to listOf(testControlHost),
                        ServerType.Cassandra to listOf(testDbHost, testDbHost2, testDbHost3),
                    ),
                s3Bucket = "test-bucket",
            )

        whenever(mockClusterStateManager.load()).thenReturn(stateWithControlAndDb)
        whenever(
            mockK8sService.createClickHouseS3Secret(
                any(),
                eq(Constants.ClickHouse.NAMESPACE),
                eq("us-west-2"),
                eq("test-bucket"),
            ),
        ).thenReturn(Result.success(Unit))
        whenever(mockK8sService.applyManifests(any(), any()))
            .thenReturn(Result.failure(RuntimeException("kubectl apply failed")))

        val command = ClickHouseStart()

        // When/Then
        assertThatThrownBy { command.execute() }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("kubectl apply failed")
    }

    @Test
    fun `execute should use custom timeout value`() {
        // Given - cluster state with control node, db nodes, and S3 bucket
        val stateWithControlAndDb =
            ClusterState(
                name = "test-cluster",
                versions = mutableMapOf(),
                hosts =
                    mutableMapOf(
                        ServerType.Control to listOf(testControlHost),
                        ServerType.Cassandra to listOf(testDbHost, testDbHost2, testDbHost3),
                    ),
                s3Bucket = "test-bucket",
            )

        whenever(mockClusterStateManager.load()).thenReturn(stateWithControlAndDb)
        whenever(mockK8sService.applyManifests(any(), any())).thenReturn(Result.success(Unit))
        whenever(
            mockK8sService.createClickHouseS3Secret(
                any(),
                eq(Constants.ClickHouse.NAMESPACE),
                eq("us-west-2"),
                eq("test-bucket"),
            ),
        ).thenReturn(Result.success(Unit))
        whenever(mockK8sService.waitForPodsReady(any(), eq(600), eq(Constants.ClickHouse.NAMESPACE)))
            .thenReturn(Result.success(Unit))

        val command = ClickHouseStart()
        command.timeoutSeconds = 600

        // When
        command.execute()

        // Then
        verify(mockK8sService).waitForPodsReady(any(), eq(600), eq(Constants.ClickHouse.NAMESPACE))
    }

    @Test
    fun `execute should fail when no db nodes exist`() {
        // Given - cluster state with control node but no db nodes
        val stateWithControlOnly =
            ClusterState(
                name = "test-cluster",
                versions = mutableMapOf(),
                hosts =
                    mutableMapOf(
                        ServerType.Control to listOf(testControlHost),
                    ),
                s3Bucket = "test-bucket",
            )

        whenever(mockClusterStateManager.load()).thenReturn(stateWithControlOnly)

        val command = ClickHouseStart()

        // When/Then
        assertThatThrownBy { command.execute() }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("No db nodes found")
    }

    @Test
    fun `execute should fail when fewer than 3 db nodes exist`() {
        // Given - cluster state with control node but only 2 db nodes (less than minimum required)
        val stateWithInsufficientNodes =
            ClusterState(
                name = "test-cluster",
                versions = mutableMapOf(),
                hosts =
                    mutableMapOf(
                        ServerType.Control to listOf(testControlHost),
                        ServerType.Cassandra to listOf(testDbHost, testDbHost2),
                    ),
                s3Bucket = "test-bucket",
            )

        whenever(mockClusterStateManager.load()).thenReturn(stateWithInsufficientNodes)

        val command = ClickHouseStart()

        // When/Then
        assertThatThrownBy { command.execute() }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("requires at least")
            .hasMessageContaining("3 nodes")
    }

    @Test
    fun `execute should warn when S3 secret creation fails but continue`() {
        // Given - cluster state with control node, db nodes, and S3 bucket
        val stateWithControlAndDb =
            ClusterState(
                name = "test-cluster",
                versions = mutableMapOf(),
                hosts =
                    mutableMapOf(
                        ServerType.Control to listOf(testControlHost),
                        ServerType.Cassandra to listOf(testDbHost, testDbHost2, testDbHost3),
                    ),
                s3Bucket = "test-bucket",
            )

        whenever(mockClusterStateManager.load()).thenReturn(stateWithControlAndDb)
        whenever(mockK8sService.applyManifests(any(), any())).thenReturn(Result.success(Unit))
        whenever(mockK8sService.createClickHouseS3Secret(any(), any(), any(), any()))
            .thenReturn(Result.failure(RuntimeException("Failed to create secret")))
        whenever(mockK8sService.waitForPodsReady(any(), any(), eq(Constants.ClickHouse.NAMESPACE)))
            .thenReturn(Result.success(Unit))

        val command = ClickHouseStart()

        // When - should not throw, just warn
        command.execute()

        // Then - manifests should still be applied despite S3 secret failure
        verify(mockK8sService).applyManifests(eq(testControlHost), eq(Path.of("k8s/clickhouse")))
        verify(mockK8sService).waitForPodsReady(eq(testControlHost), any(), eq(Constants.ClickHouse.NAMESPACE))
    }
}
