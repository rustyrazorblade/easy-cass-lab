package com.rustyrazorblade.easycasslab

import com.rustyrazorblade.easycasslab.configuration.Host
import com.rustyrazorblade.easycasslab.di.contextModule
import com.rustyrazorblade.easycasslab.ssh.MockSSHClient
import com.rustyrazorblade.easycasslab.ssh.Response
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.module.Module
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.spy
import org.mockito.kotlin.whenever

class ContextTest : BaseKoinTest() {
    private lateinit var context: Context
    private lateinit var mockSSHClient: MockSSHClient
    private lateinit var host: Host

    companion object {
        const val CASSANDRA_BASE_PATH = "/usr/local/cassandra/"
        const val TEST_VERSION = "5.0"
        const val TEST_VERSION_PATH = "/usr/local/cassandra/5.0"
        const val TEST_HOST_IP = "10.0.0.1"
        const val TEST_HOST_NAME = "test-host"
    }

    init {
        // Initialize context early so it's available for module creation
        context = spy(Context.testContext())
    }

    override fun additionalTestModules(): List<Module> =
        listOf(
            com.rustyrazorblade.easycasslab.providers.docker.dockerModule,
            contextModule(context),
        )

    @BeforeEach
    fun setup() {
        mockSSHClient = MockSSHClient()
        host = Host(TEST_HOST_NAME, TEST_HOST_IP, "", "seed")
    }

    @Test
    fun testGetRemoteVersionWithInputVersion() {
        // Given a specific version "5.0"
        // Replace the function with a lambda that always returns our mock client
        doReturn(Response(TEST_VERSION_PATH))
            .whenever(context).executeRemotely(eq(host), any(), any(), any())

        // When getting the version with the input version
        val result = context.getRemoteVersion(host, TEST_VERSION)

        assertThat(result).isNotNull()
        // Then verify the version component is correct and path is formed properly
        assertEquals(TEST_VERSION, result.versionString)
        assertEquals(TEST_VERSION_PATH, result.path)
        // No SSH command should be executed since we provided a specific version
        assertTrue(mockSSHClient.executedCommands.isEmpty())
    }

    @Test
    fun testGetRemoteVersionFromCurrentSymlink() {
        // Given the remote command will return a path to version 5.0
        mockSSHClient.commandOutput = TEST_VERSION_PATH

        doReturn(Response(TEST_VERSION_PATH))
            .whenever(context).executeRemotely(eq(host), any(), any(), any())

        // When getting the current version
        val result = context.getRemoteVersion(host, "current")

        // Then verify the version is extracted correctly
        assertEquals(TEST_VERSION, result.versionString)
        assertEquals(TEST_VERSION_PATH, result.path)
    }

    @Test
    fun `test getRemoteVersion with version containing dots`() {
        // Given a version with multiple dots
        val versionWithDots = "4.1.2"
        val expectedPath = "${CASSANDRA_BASE_PATH}$versionWithDots"
        
        doReturn(Response(expectedPath))
            .whenever(context).executeRemotely(eq(host), any(), any(), any())

        // When getting the version
        val result = context.getRemoteVersion(host, versionWithDots)

        // Then verify the version is handled correctly
        assertThat(result).isNotNull()
        assertEquals(versionWithDots, result.versionString)
        assertEquals(expectedPath, result.path)
    }

    @Test
    fun `test getRemoteVersion with snapshot version`() {
        // Given a snapshot version
        val snapshotVersion = "5.0-SNAPSHOT"
        val expectedPath = "${CASSANDRA_BASE_PATH}$snapshotVersion"
        
        doReturn(Response(expectedPath))
            .whenever(context).executeRemotely(eq(host), any(), any(), any())

        // When getting the version
        val result = context.getRemoteVersion(host, snapshotVersion)

        // Then verify snapshot versions are handled correctly
        assertThat(result).isNotNull()
        assertEquals(snapshotVersion, result.versionString)
        assertEquals(expectedPath, result.path)
    }

    @Test
    fun `test getRemoteVersion with alpha version`() {
        // Given an alpha version
        val alphaVersion = "5.1-alpha1"
        val expectedPath = "${CASSANDRA_BASE_PATH}$alphaVersion"
        
        doReturn(Response(expectedPath))
            .whenever(context).executeRemotely(eq(host), any(), any(), any())

        // When getting the version
        val result = context.getRemoteVersion(host, alphaVersion)

        // Then verify alpha versions are handled correctly
        assertThat(result).isNotNull()
        assertEquals(alphaVersion, result.versionString)
        assertEquals(expectedPath, result.path)
    }

    @Test
    fun `test getRemoteVersion with beta version`() {
        // Given a beta version
        val betaVersion = "5.1-beta2"
        val expectedPath = "${CASSANDRA_BASE_PATH}$betaVersion"
        
        doReturn(Response(expectedPath))
            .whenever(context).executeRemotely(eq(host), any(), any(), any())

        // When getting the version
        val result = context.getRemoteVersion(host, betaVersion)

        // Then verify beta versions are handled correctly
        assertThat(result).isNotNull()
        assertEquals(betaVersion, result.versionString)
        assertEquals(expectedPath, result.path)
    }

    @Test
    fun `test getRemoteVersion with release candidate version`() {
        // Given a release candidate version
        val rcVersion = "5.1-rc1"
        val expectedPath = "${CASSANDRA_BASE_PATH}$rcVersion"
        
        doReturn(Response(expectedPath))
            .whenever(context).executeRemotely(eq(host), any(), any(), any())

        // When getting the version
        val result = context.getRemoteVersion(host, rcVersion)

        // Then verify RC versions are handled correctly
        assertThat(result).isNotNull()
        assertEquals(rcVersion, result.versionString)
        assertEquals(expectedPath, result.path)
    }
}
