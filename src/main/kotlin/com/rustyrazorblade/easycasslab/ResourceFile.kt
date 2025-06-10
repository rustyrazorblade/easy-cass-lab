package com.rustyrazorblade.easycasslab

import org.apache.commons.io.FileUtils
import org.apache.commons.io.FilenameUtils
import org.apache.commons.io.IOUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.File
import java.io.InputStream

/**
 * Creates a temporary file from a resource location
 */
class ResourceFile(val resource: InputStream) {

    val fp: File
    val log = KotlinLogging.logger {}

    init {
        checkNotNull(resource)

        val tmpDir = File(System.getProperty("user.home"), ".easy-cass-lab/tmp")

        if(!tmpDir.exists()) {
            log.debug { "Creating temporary directory at $tmpDir" }
            tmpDir.mkdirs()
        }

        fp = File.createTempFile(
                FilenameUtils.getBaseName("resource"),
                FilenameUtils.getExtension("tmp"),
                tmpDir)

        IOUtils.copy(resource, FileUtils.openOutputStream(fp))

    }

    val path : String
        get() = fp.absolutePath

}