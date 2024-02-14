package com.rustyrazorblade.easycasslab.terraform

import  com.rustyrazorblade.easycasslab.Context
import org.junit.jupiter.api.Assertions.*

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class ConfigurationTest {

    private val c = Configuration("TEST-1", "TEST CLIENT", "Testing tags", "us-west-2", context = Context.testContext())

    @Test
    fun ensureSetVariableWorks() {
        println(c.toJSON())
    }
}