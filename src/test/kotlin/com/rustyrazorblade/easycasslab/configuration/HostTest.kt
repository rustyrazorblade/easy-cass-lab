package com.rustyrazorblade.easycasslab.configuration


import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class HostTest {
    @Test
    fun singleName() {
        val tmp = Host.fromTerraformString("aws_instance.cassandra", "test", "test", "a")
        assertThat(tmp.alias).isEqualTo("cassandra0")
    }

    @Test
    fun multipleName() {
        val tmp = Host.fromTerraformString("aws_instance.cassandra.0", "test", "test", "a")
        assertThat(tmp.alias).isEqualTo("cassandra0")

    }
}