package com.rustyrazorblade.easycasslab.containers

import  com.rustyrazorblade.easycasslab.Context
import org.apache.commons.io.FileUtils
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

class CassandraUnpackTest {
    @Test
    fun testCache() {
        val cache = Files.createTempDirectory("cache")
        unpacker = com.rustyrazorblade.easycasslab.containers.CassandraUnpack(
            context,
            "2.1.14",
            downloadDir,
            Optional.of(cache)
        )
        unpacker.download()
        Assertions.assertThat(unpacker.cacheHits).isEqualTo(0)
        Assertions.assertThat(unpacker.cacheChecks).isEqualTo(1)

        unpacker.download()
        Assertions.assertThat(unpacker.cacheChecks).isEqualTo(2)
        Assertions.assertThat(unpacker.cacheHits).isEqualTo(1)

    }

    lateinit var downloadDir : Path
    lateinit var unpacker : com.rustyrazorblade.easycasslab.containers.CassandraUnpack
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
        unpacker = com.rustyrazorblade.easycasslab.containers.CassandraUnpack(context, "2.1.14", downloadDir)
    }

    @AfterEach
    fun tearDownUnpacker() {
        FileUtils.deleteDirectory(downloadDir.toFile())
    }

    @Test
    fun ensureDownloadCreatesDebPackageAndConfFiles() {
        unpacker.download()

        Assertions.assertThat(File(downloadDir.toFile(), "cassandra_2.1.14_all.deb")).isFile()
        Assertions.assertThat(File(downloadDir.toFile(), "conf")).exists()

        unpacker.extractConf()

        Assertions.assertThat(File(downloadDir.toFile(), "conf/cassandra.yaml")).isFile()
    }
}