package com.thelastpickle.tlpcluster.containers

import com.github.dockerjava.api.model.AccessMode
import com.thelastpickle.tlpcluster.*
import org.apache.commons.io.FileUtils
import org.apache.logging.log4j.kotlin.logger
import java.io.File
import java.net.URL
import java.nio.file.Path
import java.util.*


class CassandraUnpack(val context: Context,
                      val version: String,
                      val dest: Path,
                      val cacheLocation: Optional<Path> = Optional.empty()) {

    val docker = Docker(context)

    var cacheHits = 0
    var cacheChecks = 0

    var log = logger()

    fun download() {
        // example http://dl.bintray.com/apache/cassandra/pool/main/c/cassandra/cassandra_2.1.14_all.deb
        // if using a cache, check for the version
        var found = false
        val destination = File(dest.toFile(), getFileName())

        cacheLocation.map {
            cacheChecks++
            val tmp = File(it.toFile(), getFileName())
            if(tmp.exists()) {
                println("skipping download, using cache")
                FileUtils.copyFile(tmp, destination)
                found = true
                cacheHits++
            }
        }

        if(!found) {
            println("Downloading version $version")
            FileUtils.copyURLToFile(URL(getURL()), destination)
            println("Download complete.")
            // copy file over to the cache if we're using it
            cacheLocation.map {
                FileUtils.copyFile(destination, File(it.toFile(), getFileName()))
            }
        }
        File(dest.toFile(), "conf").mkdir()
    }


    fun extractConf() : Result<String> {
        // required that the download have already run
        check(File(dest.toFile(), getFileName()).exists())

        return docker
                .addVolume(VolumeMapping(dest.toAbsolutePath().toString(), "/working", AccessMode.rw))
                .runContainer(Containers.CASSANDRA_BUILD,
                mutableListOf("sh", "/usr/local/bin/unpack_cassandra.sh", getFileName()),
                "/working/"
        )
    }

    fun getURL() = "https://archive.apache.org/dist/cassandra/3.11.7/debian/" + getFileName()
    fun getFileName() = "cassandra_${version}_all.deb"
}