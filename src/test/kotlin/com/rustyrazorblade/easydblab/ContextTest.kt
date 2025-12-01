package com.rustyrazorblade.easydblab

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ContextTest : BaseKoinTest() {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun `default Context should have isMcp false`() {
        val context = Context(tempDir)
        assertThat(context.isMcp).isFalse()
    }

    @Test
    fun `Context created with isMcp true should retain the value`() {
        val context = Context(tempDir, isMcp = true)
        assertThat(context.isMcp).isTrue()
    }

    @Test
    fun `forCli factory method should create Context with isMcp false`() {
        val context = Context.forCli(tempDir)
        assertThat(context.isMcp).isFalse()
    }

    @Test
    fun `forMcp factory method should create Context with isMcp true`() {
        val context = Context.forMcp(tempDir)
        assertThat(context.isMcp).isTrue()
    }

    @Test
    fun `Context data class equality should consider isMcp field`() {
        val context1 = Context(tempDir, isMcp = false)
        val context2 = Context(tempDir, isMcp = false)
        val context3 = Context(tempDir, isMcp = true)

        assertThat(context1).isEqualTo(context2)
        assertThat(context1).isNotEqualTo(context3)
    }
}
