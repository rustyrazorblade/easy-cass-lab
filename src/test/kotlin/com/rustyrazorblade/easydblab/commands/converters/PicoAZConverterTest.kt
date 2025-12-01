package com.rustyrazorblade.easydblab.commands.converters

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PicoAZConverterTest {
    private val converter = PicoAZConverter()

    @Test
    fun `converts concatenated letters to list`() {
        val result = converter.convert("abc")
        assertThat(result).containsExactly("a", "b", "c")
    }

    @Test
    fun `converts comma-separated letters to list`() {
        val result = converter.convert("a,b,c")
        assertThat(result).containsExactly("a", "b", "c")
    }

    @Test
    fun `converts space-separated letters to list`() {
        val result = converter.convert("a b c")
        assertThat(result).containsExactly("a", "b", "c")
    }

    @Test
    fun `handles mixed separators`() {
        val result = converter.convert("a b , c")
        assertThat(result).containsExactly("a", "b", "c")
    }

    @Test
    fun `filters out uppercase letters`() {
        val result = converter.convert("aBc")
        assertThat(result).containsExactly("a", "c")
    }

    @Test
    fun `filters out numbers and special characters`() {
        val result = converter.convert("a1b2c!")
        assertThat(result).containsExactly("a", "b", "c")
    }

    @Test
    fun `returns empty list for empty input`() {
        val result = converter.convert("")
        assertThat(result).isEmpty()
    }

    @Test
    fun `returns empty list for input with no valid AZ letters`() {
        val result = converter.convert("123!@#")
        assertThat(result).isEmpty()
    }

    @Test
    fun `handles single availability zone`() {
        val result = converter.convert("a")
        assertThat(result).containsExactly("a")
    }

    @Test
    fun `preserves order of availability zones`() {
        val result = converter.convert("cba")
        assertThat(result).containsExactly("c", "b", "a")
    }
}
