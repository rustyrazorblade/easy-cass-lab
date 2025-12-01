package com.rustyrazorblade.easydblab.commands.converters

import com.rustyrazorblade.easydblab.configuration.ServerType
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import picocli.CommandLine.TypeConversionException

class PicoServerTypeConverterTest {
    private val converter = PicoServerTypeConverter()

    @Test
    fun `converts cassandra to ServerType Cassandra`() {
        val result = converter.convert("cassandra")
        assertThat(result).isEqualTo(ServerType.Cassandra)
    }

    @Test
    fun `converts stress to ServerType Stress`() {
        val result = converter.convert("stress")
        assertThat(result).isEqualTo(ServerType.Stress)
    }

    @Test
    fun `converts control to ServerType Control`() {
        val result = converter.convert("control")
        assertThat(result).isEqualTo(ServerType.Control)
    }

    @Test
    fun `handles uppercase input`() {
        assertThat(converter.convert("CASSANDRA")).isEqualTo(ServerType.Cassandra)
        assertThat(converter.convert("STRESS")).isEqualTo(ServerType.Stress)
        assertThat(converter.convert("CONTROL")).isEqualTo(ServerType.Control)
    }

    @Test
    fun `handles mixed case input`() {
        assertThat(converter.convert("Cassandra")).isEqualTo(ServerType.Cassandra)
        assertThat(converter.convert("CasSanDra")).isEqualTo(ServerType.Cassandra)
    }

    @Test
    fun `trims whitespace from input`() {
        assertThat(converter.convert("  cassandra  ")).isEqualTo(ServerType.Cassandra)
        assertThat(converter.convert("\tstress\t")).isEqualTo(ServerType.Stress)
    }

    @Test
    fun `throws TypeConversionException for invalid server type`() {
        assertThatThrownBy { converter.convert("invalid") }
            .isInstanceOf(TypeConversionException::class.java)
            .hasMessageContaining("Invalid server type: invalid")
            .hasMessageContaining("cassandra, stress, or control")
    }

    @Test
    fun `throws TypeConversionException for empty input`() {
        assertThatThrownBy { converter.convert("") }
            .isInstanceOf(TypeConversionException::class.java)
            .hasMessageContaining("Invalid server type")
    }

    @Test
    fun `throws TypeConversionException for whitespace-only input`() {
        assertThatThrownBy { converter.convert("   ") }
            .isInstanceOf(TypeConversionException::class.java)
            .hasMessageContaining("Invalid server type")
    }
}
