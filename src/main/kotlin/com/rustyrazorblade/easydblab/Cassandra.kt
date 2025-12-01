package com.rustyrazorblade.easydblab

import java.io.File

/**
 * Manages a the Cassandra build process
 */
class Cassandra {
    // Build directory location - consider making this configurable via environment variable
    val buildDir = File(System.getProperty("user.home"), "/.easy-db-lab/builds")

    fun listBuilds(): List<String> = buildDir.listFiles().filter { it.isDirectory }.map { it.name }
}
