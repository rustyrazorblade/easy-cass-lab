package com.rustyrazorblade.easycasslab.configuration

import  com.rustyrazorblade.easycasslab.Context
import org.apache.logging.log4j.kotlin.logger

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.StringWriter

internal class TFStateTest {

    val context = Context.testContext()
    val state = TFState(context, this.javaClass.getResourceAsStream("terraform.tfstate"))
    val log = logger()

    @Test
    fun testBasicStuffIsReturned() {
        val nodes = state.getHosts(ServerType.Cassandra)
        assertThat(nodes.count()).isEqualTo(3)

        log.info { "Node0: ${nodes[0]}" }
        val node0 = nodes[0]
        assertThat(node0.alias).isEqualTo("cassandra0")
        assertThat(node0.private).isNotBlank()
        assertThat(node0.public).isNotBlank()

    }

    @Test
    fun testMonitoringReturnsOne() {
        val nodes = state.getHosts(ServerType.Monitoring)
        assertThat(nodes).hasSize(1)
    }

    @Test
    fun testCassandraIsReturned() {
        val cass = state.getHosts(ServerType.Cassandra)
        assertThat(cass).isNotEmpty()
    }

    @Test
    fun testWriteSSHConfig() {
        val tmp = StringWriter()
        val writer = tmp.buffered()
        state.writeSshConfig(writer)
        val result = tmp.buffer.toString()

        log.info { result }
        assertThat(result).contains("Host cassandra0")
    }

    @Test
    fun testWriteEnvironmentFile() {
        val tmp = StringWriter()
        val writer = tmp.buffered()
        state.writeEnvironmentFile(writer)
        val result = writer.toString()
        assertThat(result.length).isGreaterThan(0)

    }
}