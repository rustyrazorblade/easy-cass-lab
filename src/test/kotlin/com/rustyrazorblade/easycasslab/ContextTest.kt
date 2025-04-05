package com.rustyrazorblade.easycasslab

import com.rustyrazorblade.easycasslab.configuration.Host
import com.rustyrazorblade.easycasslab.ssh.MockSSHClient
import com.rustyrazorblade.easycasslab.ssh.Response
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.assertj.core.api.Assertions.assertThat
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.spy

class ContextTest {

    private lateinit var context: Context
    private lateinit var mockSSHClient: MockSSHClient
    private lateinit var host: Host
    
    @BeforeEach
    fun setup() {
        // Create test context
        context = spy(Context.testContext())
        mockSSHClient = MockSSHClient()
        host = Host("test-host", "10.0.0.1", "", "seed")

    }
    
    @Test
    fun testGetRemoteVersionWithInputVersion() {
        // Given a specific version "5.0"
        // Replace the function with a lambda that always returns our mock client
        doReturn(Response("/usr/local/cassandra/5.0")).whenever(context).executeRemotely(eq(host), any(), any(), any())
//        whenever(context.executeRemotely(eq(host), any(), any(), any())).thenReturn(Response("/usr/local/cassandra/5.0"))

        val expectedVersion = "5.0"
        // When getting the version with the input version
        val result = context.getRemoteVersion(host, expectedVersion)

        assertThat(result).isNotNull()
        // Then verify the version component is correct and path is formed properly
        assertEquals(expectedVersion, result.version)
        assertEquals("/usr/local/cassandra/5.0", result.path)
        // No SSH command should be executed since we provided a specific version
        assertTrue(mockSSHClient.executedCommands.isEmpty())
    }
    
    @Test
    fun testGetRemoteVersionFromCurrentSymlink() {
        // Given the remote command will return a path to version 5.0
        mockSSHClient.commandOutput = "/usr/local/cassandra/5.0"

        doReturn(Response("/usr/local/cassandra/5.0")).whenever(context).executeRemotely(eq(host), any(), any(), any())

        // When getting the current version
        val result = context.getRemoteVersion(host, "current")

        // Then verify the version is extracted correctly
        assertEquals("5.0", result.version)
        assertEquals("/usr/local/cassandra/5.0", result.path)
    }
}