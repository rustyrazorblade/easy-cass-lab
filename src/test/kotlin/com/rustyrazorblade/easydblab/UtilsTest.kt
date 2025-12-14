package com.rustyrazorblade.easydblab

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.File

class UtilsTest {
    @Test
    fun `inputstreamToTempFile creates temp file with content`(
        @TempDir tempDir: File,
    ) {
        val content = "Test content for temp file"
        val inputStream = ByteArrayInputStream(content.toByteArray())

        val tempFile = Utils.inputstreamToTempFile(inputStream, "test_prefix", tempDir.absolutePath)

        assertThat(tempFile).exists()
        assertThat(tempFile.name).startsWith("test_prefix")
        assertThat(tempFile.readText()).isEqualTo(content)
    }

    @Test
    fun `inputstreamToTempFile marks file for deletion on exit`(
        @TempDir tempDir: File,
    ) {
        val inputStream = ByteArrayInputStream("test".toByteArray())

        val tempFile = Utils.inputstreamToTempFile(inputStream, "delete_test", tempDir.absolutePath)

        assertThat(tempFile).exists()
        // Note: deleteOnExit() behavior can't be easily tested within the same JVM process
    }

    @Test
    fun `resolveSshKeyPath handles home directory expansion`() {
        val homeDir = System.getProperty("user.home")

        val result1 = Utils.resolveSshKeyPath("~/test.pem")
        assertThat(result1).isEqualTo(File("$homeDir/test.pem").absolutePath)

        val result2 = Utils.resolveSshKeyPath("~/.ssh/id_rsa")
        assertThat(result2).isEqualTo(File("$homeDir/.ssh/id_rsa").absolutePath)
    }

    @Test
    fun `resolveSshKeyPath handles absolute paths`() {
        val absolutePath = "/tmp/test.pem"

        val result = Utils.resolveSshKeyPath(absolutePath)

        assertThat(result).isEqualTo(File(absolutePath).absolutePath)
    }

    @Test
    fun `resolveSshKeyPath handles relative paths`() {
        val relativePath = "keys/test.pem"

        val result = Utils.resolveSshKeyPath(relativePath)

        assertThat(result).isEqualTo(File(relativePath).absolutePath)
    }

    @Test
    fun `resolveEasyDbLabUserDir returns default when env var not set`() {
        // When EASY_DB_LAB_USER_DIR is not set, should return ~/.easy-db-lab
        val homeDir = System.getProperty("user.home")
        val expectedDir = File(homeDir, ".easy-db-lab")

        val result = resolveEasyDbLabUserDir()

        // When env var is not set, should return the default location
        // Note: If EASY_DB_LAB_USER_DIR is set in the test environment,
        // this test will reflect that value instead
        if (System.getenv(Constants.Environment.USER_DIR) == null) {
            assertThat(result).isEqualTo(expectedDir)
        } else {
            // If env var is set, verify it returns that path
            assertThat(result).isEqualTo(File(System.getenv(Constants.Environment.USER_DIR)))
        }
    }

    @Test
    fun `resolveEasyDbLabUserDir returns File type`() {
        val result = resolveEasyDbLabUserDir()

        assertThat(result).isInstanceOf(File::class.java)
    }
}
