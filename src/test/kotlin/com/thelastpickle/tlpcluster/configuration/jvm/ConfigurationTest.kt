package com.thelastpickle.tlpcluster.configuration.jvm

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import org.junit.jupiter.api.BeforeEach

internal class ConfigurationTest {

    lateinit var options: Configuration

    @BeforeEach
    fun setup() {
        val stream = this.javaClass.getResourceAsStream("jvm.options")
        options = Configuration.fromInputStream(stream)
    }

    @Test
    fun testLength() {
        assertThat(options.length()).isGreaterThan(0)
    }


    @Test
    fun testGetEmptyOption() {
        val result = options.get("-Xmx")
        assertThat(result).isEmpty()
    }

    @Test
    fun testGetExistingOption() {
        options.get("")
    }

    @Test
    fun testSetHeap() {
        options.setOption("-XX:+FlightRecorder")
    }
}