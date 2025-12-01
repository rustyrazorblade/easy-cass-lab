package com.rustyrazorblade.easydblab.configuration

import org.junit.jupiter.api.Test

internal class CassandraYamlTest {
    val yaml: CassandraYaml

    init {
        val tmp = this.javaClass.getResourceAsStream("cassandra.yaml")
        yaml = CassandraYaml.create(tmp)
    }

    @Test
    fun setPropertyTest() {
        yaml.setSeeds(listOf("192.168.0.1"))
    }
}
