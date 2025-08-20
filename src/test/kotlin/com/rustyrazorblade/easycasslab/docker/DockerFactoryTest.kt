package com.rustyrazorblade.easycasslab.docker

import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.DockerClientInterface
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class DockerFactoryTest {
    
    private lateinit var mockContext: Context
    private lateinit var mockDockerClient: DockerClientInterface
    
    @BeforeEach
    fun setup() {
        mockContext = mock()
        mockDockerClient = mock()
    }
    
    @Test
    fun `createConsoleDocker creates Docker with ConsoleOutputHandler`() {
        val docker = DockerFactory.createConsoleDocker(mockContext, mockDockerClient)
        
        assertNotNull(docker)
        assertEquals(mockContext, docker.context)
    }
    
    @Test
    fun `createLoggerDocker creates Docker with LoggerOutputHandler`() {
        val docker = DockerFactory.createLoggerDocker(
            mockContext,
            mockDockerClient,
            "TestLogger"
        )
        
        assertNotNull(docker)
        assertEquals(mockContext, docker.context)
    }
    
    @Test
    fun `createBufferedDocker returns Docker and BufferedOutputHandler`() {
        val (docker, handler) = DockerFactory.createBufferedDocker(mockContext, mockDockerClient)
        
        assertNotNull(docker)
        assertNotNull(handler)
        assertEquals(mockContext, docker.context)
        assertTrue(handler is BufferedOutputHandler)
    }
    
    @Test
    fun `createCompositeDocker creates Docker with multiple handlers`() {
        val handler1 = ConsoleOutputHandler()
        val handler2 = BufferedOutputHandler()
        
        val docker = DockerFactory.createCompositeDocker(
            mockContext,
            mockDockerClient,
            handler1,
            handler2
        )
        
        assertNotNull(docker)
        assertEquals(mockContext, docker.context)
    }
    
    @Test
    fun `createCustomDocker creates Docker with custom handler`() {
        val customHandler = BufferedOutputHandler()
        
        val docker = DockerFactory.createCustomDocker(
            mockContext,
            mockDockerClient,
            customHandler
        )
        
        assertNotNull(docker)
        assertEquals(mockContext, docker.context)
    }
}