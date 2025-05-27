package com.rustyrazorblade.easycasslab

import java.io.File

/**
 * Manages a the Cassandra build process
 */
class Cassandra {
    // FIXME: un-hardcode
    val buildDir = File(System.getProperty("user.home"), "/.easy-cass-lab/builds")

    fun listBuilds(): List<String> {
        return buildDir.listFiles().filter { it.isDirectory }.map { it.name }
    }
}
