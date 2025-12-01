package com.rustyrazorblade.easydblab.ssh

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ResponseTest {
    @Test
    fun `Response data class stores text correctly`() {
        val response = Response("Command output")

        assertThat(response.text).isEqualTo("Command output")
    }

    @Test
    fun `Response data class equals and hashCode work correctly`() {
        val response1 = Response("Same text")
        val response2 = Response("Same text")
        val response3 = Response("Different text")

        assertThat(response1).isEqualTo(response2)
        assertThat(response1.hashCode()).isEqualTo(response2.hashCode())
        assertThat(response1).isNotEqualTo(response3)
        assertThat(response1.hashCode()).isNotEqualTo(response3.hashCode())
    }

    @Test
    fun `Response data class copy function works correctly`() {
        val original = Response("Original text")
        val copy = original.copy()
        val modified = original.copy(text = "Modified text")

        assertThat(copy).isEqualTo(original)
        assertThat(copy).isNotSameAs(original)
        assertThat(modified.text).isEqualTo("Modified text")
        assertThat(modified).isNotEqualTo(original)
    }

    @Test
    fun `Response toString includes text content`() {
        val response = Response("Test output")
        val stringRepresentation = response.toString()

        assertThat(stringRepresentation).contains("Response")
        assertThat(stringRepresentation).contains("Test output")
    }

    @Test
    fun `Response handles empty text`() {
        val response = Response("")

        assertThat(response.text).isEmpty()
    }

    @Test
    fun `Response handles multiline text`() {
        val multilineText =
            """
            Line 1
            Line 2
            Line 3
            """.trimIndent()

        val response = Response(multilineText)

        assertThat(response.text).isEqualTo(multilineText)
        assertThat(response.text.lines()).hasSize(3)
    }

    @Test
    fun `Response component functions work correctly`() {
        val response = Response("Component test", "Error test")

        val (text, stderr) = response

        assertThat(text).isEqualTo("Component test")
        assertThat(stderr).isEqualTo("Error test")
    }

    @Test
    fun `Response should store and retrieve stderr`() {
        val response = Response("Command output", "Error output")

        assertThat(response.text).isEqualTo("Command output")
        assertThat(response.stderr).isEqualTo("Error output")
    }

    @Test
    fun `Response should have empty stderr by default`() {
        val response = Response("Command output")

        assertThat(response.text).isEqualTo("Command output")
        assertThat(response.stderr).isEmpty()
    }

    @Test
    fun `Response copy function works with stderr`() {
        val original = Response("Original text", "Original error")
        val copyWithNewStderr = original.copy(stderr = "New error")

        assertThat(copyWithNewStderr.text).isEqualTo("Original text")
        assertThat(copyWithNewStderr.stderr).isEqualTo("New error")
    }

    @Test
    fun `Response equals and hashCode work with stderr`() {
        val response1 = Response("Same text", "Same error")
        val response2 = Response("Same text", "Same error")
        val response3 = Response("Same text", "Different error")

        assertThat(response1).isEqualTo(response2)
        assertThat(response1.hashCode()).isEqualTo(response2.hashCode())
        assertThat(response1).isNotEqualTo(response3)
        assertThat(response1.hashCode()).isNotEqualTo(response3.hashCode())
    }
}
