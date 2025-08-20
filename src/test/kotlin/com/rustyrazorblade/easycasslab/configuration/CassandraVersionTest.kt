package com.rustyrazorblade.easycasslab.configuration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.nio.file.Paths

class CassandraVersionTest {
    private val mainFilePath = Paths.get("packer/cassandra/cassandra_versions.yaml")
    private val extrasDirectoryPath = Paths.get(
        "src/test/resources/com/rustyrazorblade/easycasslab/configuration/extra_versions"
    )

    @Test
    fun testLoadFromMainAndExtras_ValidYaml() {
        val cassandraVersions = CassandraVersion.loadFromMainAndExtras(mainFilePath, extrasDirectoryPath)
        assertThat(cassandraVersions).isNotEmpty
        assertTrue(cassandraVersions.any { it.version == "3.0" })
        assertTrue(cassandraVersions.any { it.version == "3.11" })
        assertTrue(cassandraVersions.any { it.version == "4.0" })
        assertTrue(cassandraVersions.any { it.version == "1.2" })
    }

    @Test
    fun testYamlDoesNotHaveNulls() {
        val cassandraVersions = CassandraVersion.loadFromMainAndExtras(mainFilePath, extrasDirectoryPath)
        val output = ByteArrayOutputStream()
        CassandraVersion.write(cassandraVersions, output)
        assertThat(output).matches { !it.toString().contains("null") }
    }
}
