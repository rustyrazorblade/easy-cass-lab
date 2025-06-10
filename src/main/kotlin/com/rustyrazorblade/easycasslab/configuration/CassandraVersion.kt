package com.rustyrazorblade.easycasslab.configuration

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.File
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries

@JsonInclude(JsonInclude.Include.NON_EMPTY)
data class CassandraVersion(
    val version: String,
    val java: String,
    val python: String,

    @JsonIgnoreProperties(ignoreUnknown = true)
    val axonops: String? = null,
    @JsonIgnoreProperties(ignoreUnknown = true)
    val jvm_options: String?,
    @JsonIgnoreProperties(ignoreUnknown = true)
    val ant_flags: String? = null,
    @JsonIgnoreProperties(ignoreUnknown = true)
    val url: String? = null,
    @JsonIgnoreProperties(ignoreUnknown = true)
    val branch: String? = null,
    @JsonIgnoreProperties(ignoreUnknown = true)
    val java_build: String? = null,
    @JsonIgnoreProperties(ignoreUnknown = true)
    val jvm_config: String? = null
) {
    companion object {
        private val objectMapper = ObjectMapper(YAMLFactory()).registerKotlinModule()
        private var logger = KotlinLogging.logger {}

        fun loadFromFile(filePath: Path): List<CassandraVersion> {
            val fileContent = Files.readString(filePath)
            return objectMapper.readValue(fileContent, objectMapper.typeFactory.constructCollectionType(List::class.java, CassandraVersion::class.java))
        }

        fun loadFromMainAndExtras(mainFilePath: Path, extrasDirectoryPath: Path): List<CassandraVersion> {
            // Load main file
            val mainVersions = loadFromFile(mainFilePath).toMutableList()

            // Load each file in the extras directory
            if (extrasDirectoryPath.exists() && extrasDirectoryPath.isDirectory()) {
                val listDirectoryEntries = extrasDirectoryPath.listDirectoryEntries("*.yaml")
                logger.info("Loading from  ${listDirectoryEntries.size} extra potential files" )
                for (file in listDirectoryEntries) {
                    logger.info("Loading additional cassandra_versions file: $file")
                    val extraVersions = loadFromFile(file)
                    logger.info("Adding ${extraVersions.size} versions")
                    mainVersions.addAll(extraVersions)
                }
            } else {
                logger.info("Nothing to load")
            }

            // Remove duplicates based on the version field
            // TODO: improve the error message here
            val tmp = mainVersions.distinctBy { it.version }
            if (tmp.size != mainVersions.size) {
                throw RuntimeException("version conflict found")
            }
            return mainVersions
        }

        fun write(versions: List<CassandraVersion>, outputStream: OutputStream) {
            objectMapper.writeValue(outputStream, versions)
        }
        fun write(versions: List<CassandraVersion>, outputFile: File) {
            objectMapper.writeValue(outputFile, versions)
        }
    }

}