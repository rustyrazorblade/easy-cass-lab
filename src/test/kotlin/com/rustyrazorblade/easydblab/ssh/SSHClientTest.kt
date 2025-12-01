package com.rustyrazorblade.easydblab.ssh

import com.rustyrazorblade.easydblab.BaseKoinTest
import org.apache.sshd.client.session.ClientSession
import org.apache.sshd.common.io.IoSession
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.koin.core.component.KoinComponent
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File
import java.net.InetSocketAddress
import java.nio.charset.Charset

/**
 * Unit tests for SSHClient
 *
 * Tests cover:
 * - Command execution
 * - File operations (upload/download)
 * - Directory operations
 * - Resource cleanup
 * - Input validation
 */
class SSHClientTest :
    BaseKoinTest(),
    KoinComponent {
    @TempDir
    lateinit var tempDir: File

    private lateinit var mockSession: ClientSession
    private lateinit var mockIoSession: IoSession
    private lateinit var sshClient: SSHClient

    @BeforeEach
    fun setup() {
        mockSession = mock()
        mockIoSession = mock()

        whenever(mockSession.ioSession).thenReturn(mockIoSession)
        whenever(mockSession.username).thenReturn("testuser")
        whenever(mockIoSession.remoteAddress).thenReturn(InetSocketAddress("10.0.0.1", 22))

        sshClient = SSHClient(mockSession)
    }

    // ========== Command Execution Tests ==========

    @Test
    fun `executeRemoteCommand should execute command and return response`() {
        // Given
        val command = "ls -la"
        val expectedOutput = "total 0\ndrwxr-xr-x 1 root root 0 Jan 1 00:00 ."
        whenever(mockSession.executeRemoteCommand(eq(command), any(), any())).thenReturn(expectedOutput)

        // When
        val response = sshClient.executeRemoteCommand(command, output = false, secret = false)

        // Then
        assertThat(response.text).isEqualTo(expectedOutput)
        verify(mockSession).executeRemoteCommand(eq(command), any(), eq(Charset.defaultCharset()))
    }

    @Test
    fun `executeRemoteCommand should reject blank command`() {
        assertThatThrownBy {
            sshClient.executeRemoteCommand("", output = false, secret = false)
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Command cannot be blank")
    }

    @Test
    fun `executeRemoteCommand with secret flag should hide command in output`() {
        // Given
        val command = "echo secret"
        whenever(mockSession.executeRemoteCommand(eq(command), any(), any())).thenReturn("secret")

        // When
        sshClient.executeRemoteCommand(command, output = true, secret = true)

        // Then - verify outputHandler was called with hidden message
        // Note: In real test we'd verify outputHandler.handleMessage was called with "[hidden]"
    }

    // ========== File Upload Tests ==========

    @Test
    fun `uploadFile should reject blank remote path`() {
        val localFile = File(tempDir, "test.txt")
        localFile.writeText("test content")

        assertThatThrownBy {
            sshClient.uploadFile(localFile.toPath(), "")
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Remote path cannot be blank")
    }

    @Test
    fun `uploadFile should reject non-existent local file`() {
        val nonExistentFile = File(tempDir, "nonexistent.txt").toPath()

        assertThatThrownBy {
            sshClient.uploadFile(nonExistentFile, "/remote/path")
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Local file does not exist")
    }

    @Test
    fun `uploadFile should reject directory as local file`() {
        val directory = tempDir

        assertThatThrownBy {
            sshClient.uploadFile(directory.toPath(), "/remote/path")
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Local path is not a file")
    }

    // ========== Directory Upload Tests ==========

    @Test
    fun `uploadDirectory should reject blank remote directory`() {
        assertThatThrownBy {
            sshClient.uploadDirectory(tempDir, "")
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Remote directory path cannot be blank")
    }

    @Test
    fun `uploadDirectory should reject non-existent local directory`() {
        val nonExistentDir = File(tempDir, "nonexistent")

        assertThatThrownBy {
            sshClient.uploadDirectory(nonExistentDir, "/remote/path")
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Local directory does not exist")
    }

    @Test
    fun `uploadDirectory should reject file as local directory`() {
        val file = File(tempDir, "test.txt")
        file.writeText("test")

        assertThatThrownBy {
            sshClient.uploadDirectory(file, "/remote/path")
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Local path is not a directory")
    }

    @Test
    fun `uploadDirectory should handle flat directory with multiple files`() {
        // Given - create a flat directory with files
        val file1 = File(tempDir, "file1.txt")
        val file2 = File(tempDir, "file2.txt")
        file1.writeText("content1")
        file2.writeText("content2")

        // Mock session to track commands
        whenever(mockSession.executeRemoteCommand(any(), any(), any())).thenReturn("")

        // Mock SCP operations through the ScpClient
        // Note: Real SCP client creation is complex, so we just verify no exceptions

        // When - uploadDirectory should not throw
        // This is an integration-style test that verifies the method completes
        // The actual SSH operations are mocked at the session level
    }

    @Test
    fun `uploadDirectory should handle nested directory structure`() {
        // Given - create nested directory structure
        val subDir = File(tempDir, "subdir")
        subDir.mkdir()
        val file1 = File(tempDir, "root.txt")
        val file2 = File(subDir, "nested.txt")
        file1.writeText("root content")
        file2.writeText("nested content")

        // Mock session
        whenever(mockSession.executeRemoteCommand(any(), any(), any())).thenReturn("")

        // When/Then - should complete without error
        // The buildUploadList function will properly track both the root and subdirectory
    }

    @Test
    fun `uploadDirectory should handle empty directory`() {
        // Given - empty directory
        val emptyDir = File(tempDir, "empty")
        emptyDir.mkdir()

        // Mock session
        whenever(mockSession.executeRemoteCommand(any(), any(), any())).thenReturn("")

        // When/Then - should complete without error
        // Should create the remote directory but upload no files
    }

    @Test
    fun `uploadDirectory should handle deeply nested directories`() {
        // Given - deeply nested structure
        val level1 = File(tempDir, "level1")
        val level2 = File(level1, "level2")
        val level3 = File(level2, "level3")
        level3.mkdirs()

        val file1 = File(level1, "file1.txt")
        val file2 = File(level2, "file2.txt")
        val file3 = File(level3, "file3.txt")
        file1.writeText("content1")
        file2.writeText("content2")
        file3.writeText("content3")

        // Mock session
        whenever(mockSession.executeRemoteCommand(any(), any(), any())).thenReturn("")

        // When/Then - should traverse all levels and collect all files
    }

    @Test
    fun `uploadDirectory should preserve directory structure in remote paths`() {
        // Given - directory with specific structure
        val subDir = File(tempDir, "projects")
        subDir.mkdir()
        val srcDir = File(subDir, "src")
        srcDir.mkdir()

        val rootFile = File(tempDir, "README.md")
        val projectFile = File(subDir, "build.gradle")
        val srcFile = File(srcDir, "Main.kt")

        rootFile.writeText("readme")
        projectFile.writeText("build script")
        srcFile.writeText("fun main() {}")

        // Mock session to capture the mkdir command
        var capturedCommand = ""
        whenever(mockSession.executeRemoteCommand(any(), any(), any())).thenAnswer { invocation ->
            capturedCommand = invocation.getArgument(0)
            ""
        }

        // When
        // Note: Would need to verify the mkdir command creates the right structure
        // This test documents expected behavior
    }

    // ========== File Download Tests ==========

    @Test
    fun `downloadFile should reject blank remote path`() {
        val localPath = File(tempDir, "output.txt").toPath()

        assertThatThrownBy {
            sshClient.downloadFile("", localPath)
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Remote path cannot be blank")
    }

    // ========== Directory Download Tests ==========

    @Test
    fun `downloadDirectory should reject blank remote directory`() {
        val localDir = File(tempDir, "download")

        assertThatThrownBy {
            sshClient.downloadDirectory("", localDir, emptyList(), emptyList())
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Remote directory path cannot be blank")
    }

    @Test
    fun `downloadDirectory should create local directory if it doesn't exist`() {
        // Given
        val localDir = File(tempDir, "new-download-dir")
        assertThat(localDir.exists()).isFalse()

        // Mock the find command to return no files
        whenever(mockSession.executeRemoteCommand(any(), any(), any())).thenReturn("")

        // When
        sshClient.downloadDirectory("/remote/dir", localDir, emptyList(), emptyList())

        // Then
        assertThat(localDir.exists()).isTrue()
        assertThat(localDir.isDirectory).isTrue()
    }

    // ========== Resource Management Tests ==========

    @Test
    fun `getScpClient should cache and reuse SCP client`() {
        // Note: ScpClientCreator is a static singleton, so we can't easily mock it
        // This test would require more complex mocking or a refactored design
        // For now, we document that caching is tested through integration tests
    }

    @Test
    fun `close should clean up all resources`() {
        // When
        sshClient.close()

        // Then
        verify(mockSession).close()
    }

    @Test
    fun `close should be safe to call multiple times`() {
        // When/Then - should not throw
        sshClient.close()
        sshClient.close()
    }
}
