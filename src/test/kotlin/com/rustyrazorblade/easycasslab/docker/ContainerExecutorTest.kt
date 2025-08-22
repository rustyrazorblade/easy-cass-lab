package com.rustyrazorblade.easycasslab.docker

import com.github.dockerjava.api.async.ResultCallback
import com.github.dockerjava.api.command.InspectContainerResponse
import com.github.dockerjava.api.exception.DockerException
import com.github.dockerjava.api.model.Frame
import com.github.dockerjava.api.model.StreamType
import com.rustyrazorblade.easycasslab.DockerClientInterface
import com.rustyrazorblade.easycasslab.output.BufferedOutputHandler
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.IOException

class ContainerExecutorTest {
    private lateinit var mockDockerClient: DockerClientInterface
    private lateinit var bufferedOutputHandler: BufferedOutputHandler
    private lateinit var containerExecutor: ContainerExecutor

    @BeforeEach
    fun setup() {
        mockDockerClient = mock()
        bufferedOutputHandler = BufferedOutputHandler()
        containerExecutor = ContainerExecutor(mockDockerClient, bufferedOutputHandler)
    }

    @Test
    fun `startAndWaitForCompletion starts container and waits for completion`() {
        // Given
        val containerId = "test-container-123"
        val mockState = mock<InspectContainerResponse.ContainerState>()
        val mockInspectResponse = mock<InspectContainerResponse>()

        whenever(mockInspectResponse.state).thenReturn(mockState)
        whenever(mockDockerClient.inspectContainer(containerId)).thenReturn(mockInspectResponse)
        whenever(mockState.running).thenReturn(true).thenReturn(false)
        whenever(mockState.exitCodeLong).thenReturn(0L)

        // When
        val result = containerExecutor.startAndWaitForCompletion(containerId)

        // Then
        verify(mockDockerClient).startContainer(containerId)
        verify(mockDockerClient, atLeast(2)).inspectContainer(containerId)
        assertEquals(mockState, result)
        assertTrue(bufferedOutputHandler.messages.any { it.contains("Starting container") })
    }

    @Test
    fun `startAndWaitForCompletion handles Docker exception`() {
        // Given
        val containerId = "test-container-123"
        whenever(mockDockerClient.startContainer(containerId))
            .thenThrow(DockerException("Connection refused", 500))

        // When/Then
        val exception =
            assertThrows<com.rustyrazorblade.easycasslab.DockerException> {
                containerExecutor.startAndWaitForCompletion(containerId)
            }

        assertTrue(exception.message?.contains("Failed to start container") == true)
        assertTrue(bufferedOutputHandler.errors.any { it.first.contains("Error starting container") })
    }

    @Test
    fun `removeContainer removes container successfully`() {
        // Given
        val containerId = "test-container-123"

        // When
        containerExecutor.removeContainer(containerId)

        // Then
        verify(mockDockerClient).removeContainer(containerId, true)
    }

    @Test
    fun `removeContainer handles exceptions gracefully`() {
        // Given
        val containerId = "test-container-123"
        whenever(mockDockerClient.removeContainer(containerId, true))
            .thenThrow(RuntimeException("Container not found"))

        // When
        containerExecutor.removeContainer(containerId)

        // Then - should not throw, just log the error
        assertTrue(bufferedOutputHandler.errors.any { it.first.contains("Failed to remove container") })
    }
}

class ContainerIOManagerTest {
    private lateinit var mockDockerClient: DockerClientInterface
    private lateinit var bufferedOutputHandler: BufferedOutputHandler
    private lateinit var ioManager: ContainerIOManager

    @BeforeEach
    fun setup() {
        mockDockerClient = mock()
        bufferedOutputHandler = BufferedOutputHandler()
        ioManager = ContainerIOManager(mockDockerClient, bufferedOutputHandler)
    }

    @Test
    fun `attachToContainer attaches and counts frames correctly`() {
        // Given
        val containerId = "test-container-123"
        val callbackCaptor = argumentCaptor<ResultCallback.Adapter<Frame>>()

        doAnswer { invocation ->
            val callback = invocation.arguments[2] as ResultCallback.Adapter<Frame>
            // Simulate some frames
            callback.onNext(Frame(StreamType.STDOUT, "Line 1\n".toByteArray()))
            callback.onNext(Frame(StreamType.STDOUT, "Line 2\n".toByteArray()))
            callback.onNext(Frame(StreamType.STDERR, "Error line\n".toByteArray()))
            null
        }.whenever(mockDockerClient).attachContainer(eq(containerId), any(), callbackCaptor.capture())

        // When
        val framesRead = ioManager.attachToContainer(containerId, false)

        // Then
        assertEquals(3, framesRead)
        assertTrue(bufferedOutputHandler.messages.contains("Attaching to running container"))
        assertEquals("Line 1\nLine 2\n", bufferedOutputHandler.stdout)
        assertEquals("Error line\n", bufferedOutputHandler.stderr)
    }

    @Test
    fun `attachToContainer handles errors correctly`() {
        // Given
        val containerId = "test-container-123"

        doAnswer { invocation ->
            val callback = invocation.arguments[2] as ResultCallback.Adapter<Frame>
            callback.onError(IOException("Connection lost"))
            null
        }.whenever(mockDockerClient).attachContainer(eq(containerId), any(), any())

        // When
        val framesRead = ioManager.attachToContainer(containerId, false)

        // Then
        assertEquals(0, framesRead)
        assertTrue(bufferedOutputHandler.errors.any { it.first.contains("Container attachment error") })
    }

    @Test
    fun `close cleans up resources`() {
        // When
        ioManager.close()

        // Then - should not throw any exceptions
        assertTrue(bufferedOutputHandler.stdout.isEmpty())
    }
}

class ContainerStateMonitorTest {
    private lateinit var mockDockerClient: DockerClientInterface
    private lateinit var bufferedOutputHandler: BufferedOutputHandler
    private lateinit var stateMonitor: ContainerStateMonitor

    @BeforeEach
    fun setup() {
        mockDockerClient = mock()
        bufferedOutputHandler = BufferedOutputHandler()
        stateMonitor = ContainerStateMonitor(mockDockerClient, bufferedOutputHandler)
    }

    @Test
    fun `buildReturnMessage formats message correctly with exit code`() {
        // Given
        val mockState = mock<InspectContainerResponse.ContainerState>()
        whenever(mockState.exitCodeLong).thenReturn(0L)
        whenever(mockState.error).thenReturn(null)

        // When
        val message = stateMonitor.buildReturnMessage(mockState, 42)

        // Then
        assertEquals("Container exited with exit code 0, frames read: 42", message)
    }

    @Test
    fun `buildReturnMessage includes error when present`() {
        // Given
        val mockState = mock<InspectContainerResponse.ContainerState>()
        whenever(mockState.exitCodeLong).thenReturn(1L)
        whenever(mockState.error).thenReturn("Out of memory")

        // When
        val message = stateMonitor.buildReturnMessage(mockState, 10)

        // Then
        assertEquals("Container exited with exit code 1, Out of memory, frames read: 10", message)
    }

    @Test
    fun `reportFinalState logs message correctly`() {
        // Given
        val mockState = mock<InspectContainerResponse.ContainerState>()
        whenever(mockState.exitCodeLong).thenReturn(0L)
        whenever(mockState.error).thenReturn(null)

        // When
        stateMonitor.reportFinalState(mockState, 25)

        // Then
        assertTrue(
            bufferedOutputHandler.messages.any {
                it.contains("Container exited with exit code 0") && it.contains("frames read: 25")
            },
        )
    }
}
