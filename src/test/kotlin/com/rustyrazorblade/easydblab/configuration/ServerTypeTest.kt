package com.rustyrazorblade.easydblab.configuration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ServerTypeTest {
    @Test
    fun `ServerType enum has correct values`() {
        assertThat(ServerType.values()).hasSize(3)
        assertThat(ServerType.values().map { it.name }).containsExactly("Cassandra", "Stress", "Control")
    }

    @Test
    fun `ServerType serverType property returns correct strings`() {
        assertThat(ServerType.Cassandra.serverType).isEqualTo("db")
        assertThat(ServerType.Stress.serverType).isEqualTo("stress")
        assertThat(ServerType.Control.serverType).isEqualTo("control")
    }

    @Test
    fun `ServerType valueOf works correctly`() {
        assertThat(ServerType.valueOf("Cassandra")).isEqualTo(ServerType.Cassandra)
        assertThat(ServerType.valueOf("Stress")).isEqualTo(ServerType.Stress)
        assertThat(ServerType.valueOf("Control")).isEqualTo(ServerType.Control)
    }

    @Test
    fun `ServerType ordinal values are stable`() {
        assertThat(ServerType.Cassandra.ordinal).isEqualTo(0)
        assertThat(ServerType.Stress.ordinal).isEqualTo(1)
        assertThat(ServerType.Control.ordinal).isEqualTo(2)
    }

    @Test
    fun `ServerType values can be used in when expressions`() {
        fun getDescription(type: ServerType): String =
            when (type) {
                ServerType.Cassandra -> "Database server"
                ServerType.Stress -> "Stress testing server"
                ServerType.Control -> "Control server"
            }

        assertThat(getDescription(ServerType.Cassandra)).isEqualTo("Database server")
        assertThat(getDescription(ServerType.Stress)).isEqualTo("Stress testing server")
        assertThat(getDescription(ServerType.Control)).isEqualTo("Control server")
    }
}
