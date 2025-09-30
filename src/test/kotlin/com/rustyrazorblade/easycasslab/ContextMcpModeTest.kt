package com.rustyrazorblade.easycasslab

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ContextMcpModeTest {
    @Test
    fun `context should have isMcpMode false by default`(@TempDir tempDir: File) {
        // Given/When
        val context = Context(tempDir)

        // Then
        assertThat(context.isMcpMode).isFalse
    }

    @Test
    fun `context isMcpMode should be mutable`(@TempDir tempDir: File) {
        // Given
        val context = Context(tempDir)

        // When
        context.isMcpMode = true

        // Then
        assertThat(context.isMcpMode).isTrue

        // When
        context.isMcpMode = false

        // Then
        assertThat(context.isMcpMode).isFalse
    }

    @Test
    fun `multiple context instances should have independent isMcpMode values`(@TempDir tempDir: File) {
        // Given
        val context1 = Context(tempDir)
        val context2 = Context(tempDir)

        // When
        context1.isMcpMode = true
        context2.isMcpMode = false

        // Then
        assertThat(context1.isMcpMode).isTrue
        assertThat(context2.isMcpMode).isFalse
    }
}
