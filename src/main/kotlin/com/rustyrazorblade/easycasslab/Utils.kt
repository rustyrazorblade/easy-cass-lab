package com.rustyrazorblade.easycasslab

import org.apache.commons.io.IOUtils
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

object Utils {
        fun inputstreamToTempFile(
            inputStream: InputStream,
            prefix: String,
            directory: String,
        ): File {
            val tempFile = File.createTempFile(prefix, "", File(directory))
            tempFile.deleteOnExit()

            val outputStream = FileOutputStream(tempFile)

            IOUtils.copy(inputStream, outputStream)
            outputStream.flush()
            outputStream.close()

            return tempFile
        }

        @Deprecated(message = "Please use ResourceFile")
        fun resourceToTempFile(
            resourcePath: String,
            directory: String,
        ): File {
            val resourceName = File(resourcePath).name
            val resourceStream = this::class.java.getResourceAsStream(resourcePath)
            return Utils.inputstreamToTempFile(resourceStream, "${resourceName}_", directory)
        }

        fun prompt(
            question: String,
            default: String,
            secret: Boolean = false,
        ): String {
            print("$question [$default]: ")

            var line: String =
                if (secret) {
                    System.console()?.readPassword()?.let { String(it) }
                        ?: throw IllegalStateException("Unable to read password from console")
                } else {
                    (readLine() ?: default).trim()
                }

            if (line.equals("")) {
                line = default
            }

            return line
        }

        fun resolveSshKeyPath(keyPath: String): String {
            val sshKeyPath: String by lazy {
                var path = keyPath

                if (path.startsWith("~/")) {
                    path = path.replaceFirst("~/", "${System.getProperty("user.home")}/")
                }

                path
            }

            return File(sshKeyPath).absolutePath
        }
}
