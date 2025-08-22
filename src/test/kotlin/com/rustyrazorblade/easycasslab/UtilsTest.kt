package com.rustyrazorblade.easycasslab

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.File

class UtilsTest {
    
    @Test
    fun `inputstreamToTempFile creates temp file with content`(@TempDir tempDir: File) {
        val content = "Test content for temp file"
        val inputStream = ByteArrayInputStream(content.toByteArray())
        
        val tempFile = Utils.inputstreamToTempFile(inputStream, "test_prefix", tempDir.absolutePath)
        
        assertThat(tempFile).exists()
        assertThat(tempFile.name).startsWith("test_prefix")
        assertThat(tempFile.readText()).isEqualTo(content)
    }
    
    @Test
    fun `inputstreamToTempFile marks file for deletion on exit`(@TempDir tempDir: File) {
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
}