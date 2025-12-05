package com.rustyrazorblade.easydblab.commands.clickhouse

import com.rustyrazorblade.easydblab.BaseKoinTest
import com.rustyrazorblade.easydblab.Constants
import com.rustyrazorblade.easydblab.configuration.ClusterHost
import com.rustyrazorblade.easydblab.configuration.ClusterState
import com.rustyrazorblade.easydblab.configuration.ClusterStateManager
import com.rustyrazorblade.easydblab.configuration.ServerType
import com.rustyrazorblade.easydblab.services.K8sService
import org.assertj.core.api.Assertions.assertThat
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

/**
 * Test suite for ClickHouseStop command following TDD principles.
 *
 * These tests verify ClickHouse cluster stop and removal including
 * label-based resource deletion and safety confirmation.
 */
class ClickHouseStopTest : BaseKoinTest() {
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
    }

    @Test
    fun `command has correct default options`() {
        val command = ClickHouseStop(context)

        assertThat(command.force).isFalse()
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

        val command = ClickHouseStop(context)
        command.force = true

        // When/Then
        assertThatThrownBy { command.execute() }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("No control nodes found")
    }

    @Test
    fun `execute without force flag should not delete resources`() {
        // Given - cluster state with control node
        val stateWithControl =
            ClusterState(
                name = "test-cluster",
                versions = mutableMapOf(),
                hosts =
                    mutableMapOf(
                        ServerType.Control to listOf(testControlHost),
                    ),
            )

        whenever(mockClusterStateManager.load()).thenReturn(stateWithControl)

        val command = ClickHouseStop(context)
        command.force = false

        // When
        command.execute()

        // Then - deleteResourcesByLabel should NOT be called without --force
        verify(mockK8sService, never()).deleteResourcesByLabel(any(), any(), any(), any())
    }

    @Test
    fun `execute with force flag should delete resources by label`() {
        // Given - cluster state with control node
        val stateWithControl =
            ClusterState(
                name = "test-cluster",
                versions = mutableMapOf(),
                hosts =
                    mutableMapOf(
                        ServerType.Control to listOf(testControlHost),
                    ),
            )

        whenever(mockClusterStateManager.load()).thenReturn(stateWithControl)
        whenever(
            mockK8sService.deleteResourcesByLabel(
                any(),
                eq(Constants.ClickHouse.NAMESPACE),
                eq("app.kubernetes.io/name"),
                eq(listOf("clickhouse-server", "clickhouse-keeper")),
            ),
        ).thenReturn(Result.success(Unit))

        val command = ClickHouseStop(context)
        command.force = true

        // When
        command.execute()

        // Then
        verify(mockK8sService).deleteResourcesByLabel(
            eq(testControlHost),
            eq(Constants.ClickHouse.NAMESPACE),
            eq("app.kubernetes.io/name"),
            eq(listOf("clickhouse-server", "clickhouse-keeper")),
        )
    }

    @Test
    fun `execute should fail when deleteResourcesByLabel fails`() {
        // Given - cluster state with control node
        val stateWithControl =
            ClusterState(
                name = "test-cluster",
                versions = mutableMapOf(),
                hosts =
                    mutableMapOf(
                        ServerType.Control to listOf(testControlHost),
                    ),
            )

        whenever(mockClusterStateManager.load()).thenReturn(stateWithControl)
        whenever(
            mockK8sService.deleteResourcesByLabel(
                any(),
                eq(Constants.ClickHouse.NAMESPACE),
                eq("app.kubernetes.io/name"),
                eq(listOf("clickhouse-server", "clickhouse-keeper")),
            ),
        ).thenReturn(Result.failure(RuntimeException("Resource deletion failed")))

        val command = ClickHouseStop(context)
        command.force = true

        // When/Then
        assertThatThrownBy { command.execute() }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("Resource deletion failed")
    }

    @Test
    fun `execute should use correct ClickHouse label values for deletion`() {
        // Given - cluster state with control node
        val stateWithControl =
            ClusterState(
                name = "test-cluster",
                versions = mutableMapOf(),
                hosts =
                    mutableMapOf(
                        ServerType.Control to listOf(testControlHost),
                    ),
            )

        whenever(mockClusterStateManager.load()).thenReturn(stateWithControl)
        whenever(
            mockK8sService.deleteResourcesByLabel(
                any(),
                any(),
                any(),
                any(),
            ),
        ).thenReturn(Result.success(Unit))

        val command = ClickHouseStop(context)
        command.force = true

        // When
        command.execute()

        // Then - verify the correct label values are used
        verify(mockK8sService).deleteResourcesByLabel(
            any(),
            eq("default"),
            eq("app.kubernetes.io/name"),
            eq(listOf("clickhouse-server", "clickhouse-keeper")),
        )
    }
}
