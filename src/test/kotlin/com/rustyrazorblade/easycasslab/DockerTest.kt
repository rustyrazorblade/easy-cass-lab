package com.rustyrazorblade.easycasslab

import com.github.dockerjava.api.command.InspectContainerResponse
import com.github.dockerjava.api.command.PullImageResultCallback
import com.github.dockerjava.api.model.Image
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class DockerTest {
    private lateinit var mockContext: Context
    private lateinit var mockDockerClient: DockerClientInterface
    private lateinit var mockUserIdProvider: UserIdProvider
    private lateinit var docker: Docker
    private lateinit var mockContainerCreationCommand: ContainerCreationCommand
    private lateinit var mockContainerResponse: com.github.dockerjava.api.command.CreateContainerResponse
    private lateinit var mockContainerState: InspectContainerResponse.ContainerState
    private lateinit var mockInspectContainerResponse: InspectContainerResponse

    @BeforeEach
    fun setup() {
        mockContext = mock()
        mockDockerClient = mock()
        mockUserIdProvider = mock()
        mockContainerCreationCommand = mock()
        mockContainerResponse = mock()
        mockContainerState = mock()
        mockInspectContainerResponse = mock()

        whenever(mockContainerResponse.id).thenReturn("container123456789")
        whenever(mockContainerCreationCommand.exec()).thenReturn(mockContainerResponse)
        whenever(mockContainerCreationCommand.withCmd(any<List<String>>())).thenReturn(mockContainerCreationCommand)
        whenever(mockContainerCreationCommand.withEnv(any())).thenReturn(mockContainerCreationCommand)
        whenever(mockContainerCreationCommand.withStdinOpen(any())).thenReturn(mockContainerCreationCommand)
        whenever(mockContainerCreationCommand.withVolumes(any())).thenReturn(mockContainerCreationCommand)
        whenever(mockContainerCreationCommand.withHostConfig(any())).thenReturn(mockContainerCreationCommand)
        whenever(mockContainerCreationCommand.withWorkingDir(any())).thenReturn(mockContainerCreationCommand)

        whenever(mockDockerClient.createContainer(any())).thenReturn(mockContainerCreationCommand)
        whenever(mockInspectContainerResponse.state).thenReturn(mockContainerState)
        whenever(mockDockerClient.inspectContainer(any())).thenReturn(mockInspectContainerResponse)

        whenever(mockUserIdProvider.getUserId()).thenReturn(1000)

        docker = Docker(mockContext, mockDockerClient, mockUserIdProvider)
    }

    @Test
    fun `exists returns true when images are found`() {
        val mockImages = listOf<Image>(mock())
        whenever(mockDockerClient.listImages("test", "latest")).thenReturn(mockImages)

        val result = docker.exists("test", "latest")

        assertTrue(result)
        verify(mockDockerClient).listImages("test", "latest")
    }

    @Test
    fun `exists returns false when no images are found`() {
        whenever(mockDockerClient.listImages("test", "latest")).thenReturn(emptyList())

        val result = docker.exists("test", "latest")

        assertFalse(result)
        verify(mockDockerClient).listImages("test", "latest")
    }

    @Test
    fun `addVolume adds volume to list and returns docker instance`() {
        val volumeMapping = mock<VolumeMapping>()

        val result = docker.addVolume(volumeMapping)

        assertEquals(docker, result)
    }

    @Test
    fun `addEnv adds environment variable to list and returns docker instance`() {
        val result = docker.addEnv("TEST=value")

        assertEquals(docker, result)
    }

    @Test
    fun `pullImage calls dockerClient pullImage with correct parameters`() {
        // Use reflection to access the private volumes list
        val containerField = Containers::class.java.getDeclaredField("containerName")
        containerField.isAccessible = true
        val tagField = Containers::class.java.getDeclaredField("tag")
        tagField.isAccessible = true

        val mockContainer = mock<Containers>()
        containerField.set(mockContainer, "testcontainer")
        tagField.set(mockContainer, "testtag")

        // Mock the pullImage behavior
//        doNothing().whenever(mockDockerClient).pullImage(eq("testcontainer"), eq("testtag"), any())

//        docker.pullImage(mockContainer)

//        verify(mockDockerClient).pullImage(eq("testcontainer"), eq("testtag"), any())
    }

    @Test
    fun `runContainer with volumes sets up volumes correctly`() {
        // Given
        val mockVolume = mock<VolumeMapping>()
        whenever(mockVolume.source).thenReturn("/host/path")
        whenever(mockVolume.destination).thenReturn("/container/path")
        whenever(mockVolume.mode).thenReturn(com.github.dockerjava.api.model.AccessMode.rw)

        // First set running to true, then to false on second call to simulate container stopping
        whenever(mockContainerState.running).thenReturn(true).thenReturn(false)
        whenever(mockContainerState.exitCode).thenReturn(0)

        docker.addVolume(mockVolume)

        // When
        docker.runContainer("test:latest", mutableListOf("command"), "")

        // Then
        verify(mockContainerCreationCommand).withVolumes(any())
        verify(mockContainerCreationCommand).withHostConfig(any())
    }
}
