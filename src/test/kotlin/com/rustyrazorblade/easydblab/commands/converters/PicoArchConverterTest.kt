package com.rustyrazorblade.easydblab.commands.converters

import com.rustyrazorblade.easydblab.configuration.Arch
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import picocli.CommandLine.TypeConversionException

class PicoArchConverterTest {
    private val converter = PicoArchConverter()

    @Test
    fun `converts amd64 to Arch AMD64`() {
        val result = converter.convert("amd64")
        assertThat(result).isEqualTo(Arch.AMD64)
    }

    @Test
    fun `converts arm64 to Arch ARM64`() {
        val result = converter.convert("arm64")
        assertThat(result).isEqualTo(Arch.ARM64)
    }

    @Test
    fun `handles uppercase input`() {
        assertThat(converter.convert("AMD64")).isEqualTo(Arch.AMD64)
        assertThat(converter.convert("ARM64")).isEqualTo(Arch.ARM64)
    }

    @Test
    fun `handles mixed case input`() {
        assertThat(converter.convert("Amd64")).isEqualTo(Arch.AMD64)
        assertThat(converter.convert("ArM64")).isEqualTo(Arch.ARM64)
    }

    @Test
    fun `trims whitespace from input`() {
        assertThat(converter.convert("  amd64  ")).isEqualTo(Arch.AMD64)
        assertThat(converter.convert("\tarm64\t")).isEqualTo(Arch.ARM64)
    }

    @Test
    fun `throws TypeConversionException for invalid architecture`() {
        assertThatThrownBy { converter.convert("x86") }
            .isInstanceOf(TypeConversionException::class.java)
            .hasMessageContaining("Invalid architecture: x86")
            .hasMessageContaining("amd64 or arm64")
    }

    @Test
    fun `throws TypeConversionException for empty input`() {
        assertThatThrownBy { converter.convert("") }
            .isInstanceOf(TypeConversionException::class.java)
            .hasMessageContaining("Invalid architecture")
    }

    @Test
    fun `throws TypeConversionException for whitespace-only input`() {
        assertThatThrownBy { converter.convert("   ") }
            .isInstanceOf(TypeConversionException::class.java)
            .hasMessageContaining("Invalid architecture")
    }
}
