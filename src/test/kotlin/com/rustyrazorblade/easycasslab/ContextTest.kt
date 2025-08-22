package com.rustyrazorblade.easycasslab

import com.rustyrazorblade.easycasslab.configuration.Host
import com.rustyrazorblade.easycasslab.di.contextModule
import com.rustyrazorblade.easycasslab.ssh.MockSSHClient
import com.rustyrazorblade.easycasslab.ssh.Response
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.spy
import org.mockito.kotlin.whenever

class ContextTest {
    private lateinit var context: Context
    private lateinit var mockSSHClient: MockSSHClient
    private lateinit var host: Host

    @BeforeEach
    fun setup() {
        // Create test context
        context = spy(Context.testContext())

        // Initialize Koin for testing with test modules
        startKoin {
            modules(
                listOf(
                    com.rustyrazorblade.easycasslab.providers.docker.dockerModule,
                    TestModules.testSSHModule(),
                    contextModule(context),
                ),
            )
        }

        mockSSHClient = MockSSHClient()
        host = Host("test-host", "10.0.0.1", "", "seed")
    }

    @AfterEach
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun testGetRemoteVersionWithInputVersion() {
        // Given a specific version "5.0"
        // Replace the function with a lambda that always returns our mock client
        doReturn(Response("/usr/local/cassandra/5.0"))
            .whenever(context).executeRemotely(eq(host), any(), any(), any())

        val expectedVersion = "5.0"
        // When getting the version with the input version
        val result = context.getRemoteVersion(host, expectedVersion)

        assertThat(result).isNotNull()
        // Then verify the version component is correct and path is formed properly
        assertEquals(expectedVersion, result.versionString)
        assertEquals("/usr/local/cassandra/5.0", result.path)
        // No SSH command should be executed since we provided a specific version
        assertTrue(mockSSHClient.executedCommands.isEmpty())
    }

    @Test
    fun testGetRemoteVersionFromCurrentSymlink() {
        // Given the remote command will return a path to version 5.0
        mockSSHClient.commandOutput = "/usr/local/cassandra/5.0"

        doReturn(Response("/usr/local/cassandra/5.0"))
            .whenever(context).executeRemotely(eq(host), any(), any(), any())

        // When getting the current version
        val result = context.getRemoteVersion(host, "current")

        // Then verify the version is extracted correctly
        assertEquals("5.0", result.versionString)
        assertEquals("/usr/local/cassandra/5.0", result.path)
    }
}
