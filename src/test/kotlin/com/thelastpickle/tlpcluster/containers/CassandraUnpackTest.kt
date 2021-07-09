package com.thelastpickle.tlpcluster.containers

import com.thelastpickle.tlpcluster.Context
import org.apache.commons.io.FileUtils
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.AfterEach
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

internal class CassandraUnpackTest {

    lateinit var downloadDir : Path
    lateinit var unpacker : CassandraUnpack
    lateinit var context: Context

    @BeforeEach
    fun setupUnpacker() {
        context = Context.testContext()
        val downloadDirFile = File("test/downloads")
        downloadDirFile.mkdirs()

        downloadDir = Files.createTempDirectory(downloadDirFile.toPath(), "test")

        val downloadLocation = File("test/unpack")
        downloadLocation.mkdirs()

        if(!downloadLocation.exists()) {
            throw Exception("Unpack directory $downloadLocation does not exist")
        }

        downloadDir = Files.createTempDirectory(downloadLocation.toPath(),  "test")
        unpacker = CassandraUnpack(context, "2.1.14", downloadDir)
    }

    @AfterEach
    fun tearDownUnpacker() {
        FileUtils.deleteDirectory(downloadDir.toFile())
    }




    @Test
    fun getURL() {
        val expected = "https://archive.apache.org/dist/cassandra/3.11.7/debian/cassandra_2.1.14_all.deb"
        assertThat(unpacker.getURL()).isEqualTo(expected)
    }

    @Test
    fun getFileName() {
    }

    @Test
    fun getVersion() {
    }

    @Test
    fun getDest() {
    }

    @Test
    fun ensureDebExistsBeforeExtracting() {
        Assertions.assertThatIllegalStateException().isThrownBy { unpacker.extractConf() }
                .withMessageContaining("Check failed")

    }


}