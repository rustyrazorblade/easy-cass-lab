package com.rustyrazorblade.easycasslab.docker

import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.DockerClientInterface
import com.rustyrazorblade.easycasslab.KoinTestHelper
import com.rustyrazorblade.easycasslab.output.BufferedOutputHandler
import com.rustyrazorblade.easycasslab.output.ConsoleOutputHandler
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class DockerFactoryTest {
    private lateinit var mockContext: Context
    private lateinit var mockDockerClient: DockerClientInterface

    @BeforeEach
    fun setup() {
        KoinTestHelper.startKoin()
        
        mockContext = mock()
        mockDockerClient = mock()
    }
    
    @AfterEach
    fun teardown() {
        KoinTestHelper.stopKoin()
    }

    @Test
    fun `createConsoleDocker creates Docker with ConsoleOutputHandler`() {
        val docker = DockerFactory.createConsoleDocker(mockContext, mockDockerClient)

        assertNotNull(docker)
        assertEquals(mockContext, docker.context)
    }

    @Test
    fun `createLoggerDocker creates Docker with LoggerOutputHandler`() {
        val docker =
            DockerFactory.createLoggerDocker(
                mockContext,
                mockDockerClient,
                "TestLogger",
            )

        assertNotNull(docker)
        assertEquals(mockContext, docker.context)
    }

    @Test
    fun `createBufferedDocker returns Docker instance`() {
        val docker = DockerFactory.createBufferedDocker(mockContext, mockDockerClient)

        assertNotNull(docker)
        assertEquals(mockContext, docker.context)
    }

    @Test
    fun `createCompositeDocker creates Docker instance`() {
        val docker =
            DockerFactory.createCompositeDocker(
                mockContext,
                mockDockerClient,
            )

        assertNotNull(docker)
        assertEquals(mockContext, docker.context)
    }

    @Test
    fun `createCustomDocker creates Docker instance`() {
        val docker =
            DockerFactory.createCustomDocker(
                mockContext,
                mockDockerClient,
            )

        assertNotNull(docker)
        assertEquals(mockContext, docker.context)
    }
}
