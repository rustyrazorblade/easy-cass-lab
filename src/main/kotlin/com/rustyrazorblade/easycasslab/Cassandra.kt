package com.rustyrazorblade.easycasslab

import java.io.File

/**
 * Manages a the Cassandra build process
 */
class Cassandra {
    // Build directory location - consider making this configurable via environment variable
    val buildDir = File(System.getProperty("user.home"), "/.easy-cass-lab/builds")

    fun listBuilds(): List<String> {
        return buildDir.listFiles().filter { it.isDirectory }.map { it.name }
    }
}
