package com.rustyrazorblade.easydblab.services

import com.rustyrazorblade.easydblab.BaseKoinTest
import com.rustyrazorblade.easydblab.configuration.Host
import com.rustyrazorblade.easydblab.providers.ssh.RemoteOperationsService
import com.rustyrazorblade.easydblab.ssh.Response
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.module.Module
import org.koin.dsl.module
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Test suite for SidecarService following TDD principles.
 *
 * These tests verify cassandra-sidecar lifecycle operations (start, stop, restart)
 * and status checks using mocked SSH operations.
 */
class SidecarServiceTest : BaseKoinTest() {
    private lateinit var mockRemoteOps: RemoteOperationsService
    private lateinit var sidecarService: SidecarService

    private val testHost =
        Host(
            public = "54.123.45.67",
            private = "10.0.1.10",
            alias = "db0",
            availabilityZone = "us-west-2a",
        )

    override fun additionalTestModules(): List<Module> =
        listOf(
            module {
                single<RemoteOperationsService> { mockRemoteOps }
                factory<SidecarService> { DefaultSidecarService(get(), get()) }
            },
        )

    @BeforeEach
    fun setupMocks() {
        mockRemoteOps = mock()
        sidecarService = getKoin().get()
    }

    // ========== START OPERATION TESTS ==========

    @Test
    fun `start should execute systemctl start command successfully`() {
        // Given
        val expectedCommand = "sudo systemctl start cassandra-sidecar"
        val successResponse = Response(text = "", stderr = "")
        whenever(mockRemoteOps.executeRemotely(eq(testHost), eq(expectedCommand), any(), any()))
            .thenReturn(successResponse)

        // When
        val result = sidecarService.start(testHost)

        // Then
        assertThat(result.isSuccess).isTrue()
        verify(mockRemoteOps).executeRemotely(eq(testHost), eq(expectedCommand), any(), any())
    }

    @Test
    fun `start should return failure when SSH operation throws exception`() {
        // Given
        val expectedCommand = "sudo systemctl start cassandra-sidecar"
        whenever(mockRemoteOps.executeRemotely(eq(testHost), eq(expectedCommand), any(), any()))
            .thenThrow(RuntimeException("Connection refused"))

        // When
        val result = sidecarService.start(testHost)

        // Then
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull())
            .hasMessageContaining("Connection refused")
    }

    // ========== STOP OPERATION TESTS ==========

    @Test
    fun `stop should execute systemctl stop command successfully`() {
        // Given
        val expectedCommand = "sudo systemctl stop cassandra-sidecar"
        val successResponse = Response(text = "", stderr = "")
        whenever(mockRemoteOps.executeRemotely(eq(testHost), eq(expectedCommand), any(), any()))
            .thenReturn(successResponse)

        // When
        val result = sidecarService.stop(testHost)

        // Then
        assertThat(result.isSuccess).isTrue()
        verify(mockRemoteOps).executeRemotely(eq(testHost), eq(expectedCommand), any(), any())
    }

    @Test
    fun `stop should return failure when SSH operation throws exception`() {
        // Given
        val expectedCommand = "sudo systemctl stop cassandra-sidecar"
        whenever(mockRemoteOps.executeRemotely(eq(testHost), eq(expectedCommand), any(), any()))
            .thenThrow(RuntimeException("Connection timeout"))

        // When
        val result = sidecarService.stop(testHost)

        // Then
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull())
            .hasMessageContaining("Connection timeout")
    }

    // ========== RESTART OPERATION TESTS ==========

    @Test
    fun `restart should execute systemctl restart command successfully`() {
        // Given
        val expectedCommand = "sudo systemctl restart cassandra-sidecar"
        val successResponse = Response(text = "", stderr = "")
        whenever(mockRemoteOps.executeRemotely(eq(testHost), eq(expectedCommand), any(), any()))
            .thenReturn(successResponse)

        // When
        val result = sidecarService.restart(testHost)

        // Then
        assertThat(result.isSuccess).isTrue()
        verify(mockRemoteOps).executeRemotely(eq(testHost), eq(expectedCommand), any(), any())
    }

    @Test
    fun `restart should return failure when SSH operation throws exception`() {
        // Given
        val expectedCommand = "sudo systemctl restart cassandra-sidecar"
        whenever(mockRemoteOps.executeRemotely(eq(testHost), eq(expectedCommand), any(), any()))
            .thenThrow(RuntimeException("Timeout restarting service"))

        // When
        val result = sidecarService.restart(testHost)

        // Then
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull())
            .hasMessageContaining("Timeout restarting service")
    }

    // ========== IS RUNNING TESTS ==========

    @Test
    fun `isRunning should return true when cassandra-sidecar is active`() {
        // Given
        val expectedCommand = "sudo systemctl is-active cassandra-sidecar"
        val activeResponse = Response(text = "active", stderr = "")
        whenever(mockRemoteOps.executeRemotely(eq(testHost), eq(expectedCommand), any(), any()))
            .thenReturn(activeResponse)

        // When
        val result = sidecarService.isRunning(testHost)

        // Then
        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()).isTrue()
        verify(mockRemoteOps).executeRemotely(eq(testHost), eq(expectedCommand), any(), any())
    }

    @Test
    fun `isRunning should return false when cassandra-sidecar is inactive`() {
        // Given
        val expectedCommand = "sudo systemctl is-active cassandra-sidecar"
        val inactiveResponse = Response(text = "inactive", stderr = "")
        whenever(mockRemoteOps.executeRemotely(eq(testHost), eq(expectedCommand), any(), any()))
            .thenReturn(inactiveResponse)

        // When
        val result = sidecarService.isRunning(testHost)

        // Then
        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()).isFalse()
        verify(mockRemoteOps).executeRemotely(eq(testHost), eq(expectedCommand), any(), any())
    }

    @Test
    fun `isRunning should return false when cassandra-sidecar is in failed state`() {
        // Given
        val expectedCommand = "sudo systemctl is-active cassandra-sidecar"
        val failedResponse = Response(text = "failed", stderr = "")
        whenever(mockRemoteOps.executeRemotely(eq(testHost), eq(expectedCommand), any(), any()))
            .thenReturn(failedResponse)

        // When
        val result = sidecarService.isRunning(testHost)

        // Then
        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()).isFalse()
        verify(mockRemoteOps).executeRemotely(eq(testHost), eq(expectedCommand), any(), any())
    }

    @Test
    fun `isRunning should handle active status with whitespace`() {
        // Given
        val expectedCommand = "sudo systemctl is-active cassandra-sidecar"
        val activeResponse = Response(text = "active\n", stderr = "")
        whenever(mockRemoteOps.executeRemotely(eq(testHost), eq(expectedCommand), any(), any()))
            .thenReturn(activeResponse)

        // When
        val result = sidecarService.isRunning(testHost)

        // Then
        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()).isTrue()
    }

    @Test
    fun `isRunning should be case insensitive for active status`() {
        // Given
        val expectedCommand = "sudo systemctl is-active cassandra-sidecar"
        val activeResponse = Response(text = "ACTIVE", stderr = "")
        whenever(mockRemoteOps.executeRemotely(eq(testHost), eq(expectedCommand), any(), any()))
            .thenReturn(activeResponse)

        // When
        val result = sidecarService.isRunning(testHost)

        // Then
        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()).isTrue()
    }

    @Test
    fun `isRunning should return failure when SSH operation throws exception`() {
        // Given
        val expectedCommand = "sudo systemctl is-active cassandra-sidecar"
        whenever(mockRemoteOps.executeRemotely(eq(testHost), eq(expectedCommand), any(), any()))
            .thenThrow(RuntimeException("SSH connection lost"))

        // When
        val result = sidecarService.isRunning(testHost)

        // Then
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull())
            .hasMessageContaining("SSH connection lost")
    }

    // ========== GET STATUS TESTS ==========

    @Test
    fun `getStatus should return status output successfully`() {
        // Given
        val expectedCommand = "sudo systemctl status cassandra-sidecar"
        val statusOutput =
            """
            cassandra-sidecar.service - Cassandra Sidecar Server
               Loaded: loaded (/etc/systemd/system/cassandra-sidecar.service; enabled)
               Active: active (running) since Mon 2024-01-15 10:00:00 UTC; 2h ago
            """.trimIndent()
        val successResponse = Response(text = statusOutput, stderr = "")
        whenever(mockRemoteOps.executeRemotely(eq(testHost), eq(expectedCommand), any(), any()))
            .thenReturn(successResponse)

        // When
        val result = sidecarService.getStatus(testHost)

        // Then
        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()).isEqualTo(statusOutput)
        verify(mockRemoteOps).executeRemotely(eq(testHost), eq(expectedCommand), any(), any())
    }

    @Test
    fun `getStatus should return failure when SSH operation throws exception`() {
        // Given
        val expectedCommand = "sudo systemctl status cassandra-sidecar"
        whenever(mockRemoteOps.executeRemotely(eq(testHost), eq(expectedCommand), any(), any()))
            .thenThrow(RuntimeException("Failed to get status"))

        // When
        val result = sidecarService.getStatus(testHost)

        // Then
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull())
            .hasMessageContaining("Failed to get status")
    }
}
