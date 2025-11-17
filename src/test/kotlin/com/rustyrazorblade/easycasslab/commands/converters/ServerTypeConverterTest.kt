package com.rustyrazorblade.easycasslab.commands.converters

import com.rustyrazorblade.easycasslab.configuration.ServerType
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

internal class ServerTypeConverterTest {
    private val converter = ServerTypeConverter()

    @Test
    fun `convert cassandra lowercase returns Cassandra enum`() {
        assertThat(converter.convert("cassandra")).isEqualTo(ServerType.Cassandra)
    }

    @Test
    fun `convert stress lowercase returns Stress enum`() {
        assertThat(converter.convert("stress")).isEqualTo(ServerType.Stress)
    }

    @Test
    fun `convert control lowercase returns Control enum`() {
        assertThat(converter.convert("control")).isEqualTo(ServerType.Control)
    }

    @Test
    fun `convert handles uppercase CASSANDRA`() {
        assertThat(converter.convert("CASSANDRA")).isEqualTo(ServerType.Cassandra)
    }

    @Test
    fun `convert handles leading and trailing whitespace`() {
        assertThat(converter.convert("  cassandra  ")).isEqualTo(ServerType.Cassandra)
    }

    @Test
    fun `convert throws exception for invalid server type`() {
        assertThatThrownBy { converter.convert("invalid") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Invalid server type: invalid")
            .hasMessageContaining("Must be cassandra, stress, or control")
    }

    @Test
    fun `convert throws exception for null input`() {
        assertThatThrownBy { converter.convert(null) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Server type cannot be null")
    }

    @Test
    fun `convert throws exception for empty string`() {
        assertThatThrownBy { converter.convert("") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Invalid server type")
    }

    @Test
    fun `convert throws exception for blank string`() {
        assertThatThrownBy { converter.convert("   ") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Invalid server type")
    }
}
