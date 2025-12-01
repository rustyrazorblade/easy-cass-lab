package com.rustyrazorblade.easydblab.exceptions

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class EasyDBLabExceptionsTest {
    @Test
    fun `EasyDBLabException base exception works correctly`() {
        val exception = EasyDBLabException("Test error message")

        assertThat(exception.message).isEqualTo("Test error message")
        assertThat(exception.cause).isNull()
    }

    @Test
    fun `EasyDBLabException with cause works correctly`() {
        val cause = RuntimeException("Original error")
        val exception = EasyDBLabException("Wrapped error", cause)

        assertThat(exception.message).isEqualTo("Wrapped error")
        assertThat(exception.cause).isEqualTo(cause)
    }

    @Test
    fun `DuplicateVersionException formats message correctly`() {
        val versions = setOf("3.11.10", "4.0.0", "3.11.10")
        val exception = DuplicateVersionException(versions)

        assertThat(exception.message).contains("Duplicate Cassandra version(s) found")
        assertThat(exception.message).contains("3.11.10")
        assertThat(exception.message).contains("4.0.0")
        assertThat(exception.message).contains("Please ensure each version is unique")
    }

    @Test
    fun `DuplicateVersionException is an EasyDBLabException`() {
        val exception = DuplicateVersionException(setOf("1.0"))

        assertThat(exception).isInstanceOf(EasyDBLabException::class.java)
    }

    @Test
    fun `ConfigurationException works with message only`() {
        val exception = ConfigurationException("Invalid configuration")

        assertThat(exception.message).isEqualTo("Invalid configuration")
        assertThat(exception.cause).isNull()
        assertThat(exception).isInstanceOf(EasyDBLabException::class.java)
    }

    @Test
    fun `ConfigurationException works with cause`() {
        val cause = IllegalArgumentException("Bad argument")
        val exception = ConfigurationException("Config error", cause)

        assertThat(exception.message).isEqualTo("Config error")
        assertThat(exception.cause).isEqualTo(cause)
    }

    @Test
    fun `DockerOperationException works correctly`() {
        val exception = DockerOperationException("Docker failed to start container")

        assertThat(exception.message).isEqualTo("Docker failed to start container")
        assertThat(exception).isInstanceOf(EasyDBLabException::class.java)
    }

    @Test
    fun `CommandExecutionException works correctly`() {
        val exception = CommandExecutionException("Command failed with exit code 1")

        assertThat(exception.message).isEqualTo("Command failed with exit code 1")
        assertThat(exception).isInstanceOf(EasyDBLabException::class.java)
    }

    @Test
    fun `SSHException works correctly`() {
        val exception = SSHException("Failed to connect to host")

        assertThat(exception.message).isEqualTo("Failed to connect to host")
        assertThat(exception).isInstanceOf(EasyDBLabException::class.java)
    }

    @Test
    fun `DuplicateVersionException can be thrown and caught`() {
        assertThatThrownBy { throw DuplicateVersionException(setOf("1.0")) }
            .isInstanceOf(DuplicateVersionException::class.java)
            .isInstanceOf(EasyDBLabException::class.java)
    }

    @Test
    fun `ConfigurationException can be thrown and caught`() {
        assertThatThrownBy { throw ConfigurationException("error") }
            .isInstanceOf(ConfigurationException::class.java)
            .isInstanceOf(EasyDBLabException::class.java)
    }

    @Test
    fun `DockerOperationException can be thrown and caught`() {
        assertThatThrownBy { throw DockerOperationException("error") }
            .isInstanceOf(DockerOperationException::class.java)
            .isInstanceOf(EasyDBLabException::class.java)
    }

    @Test
    fun `CommandExecutionException can be thrown and caught`() {
        assertThatThrownBy { throw CommandExecutionException("error") }
            .isInstanceOf(CommandExecutionException::class.java)
            .isInstanceOf(EasyDBLabException::class.java)
    }

    @Test
    fun `SSHException can be thrown and caught`() {
        assertThatThrownBy { throw SSHException("error") }
            .isInstanceOf(SSHException::class.java)
            .isInstanceOf(EasyDBLabException::class.java)
    }

    @Test
    fun `Exception hierarchy allows catching by base type`() {
        val exceptions =
            listOf(
                DuplicateVersionException(setOf("1.0")),
                ConfigurationException("error"),
                DockerOperationException("error"),
                CommandExecutionException("error"),
                SSHException("error"),
            )

        exceptions.forEach { exception ->
            try {
                throw exception
            } catch (e: EasyDBLabException) {
                assertThat(e).isInstanceOf(EasyDBLabException::class.java)
            }
        }
    }
}
