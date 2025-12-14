package com.rustyrazorblade.easydblab.configuration

import java.io.InputStream

data class Seeds(
    val seeds: List<String>,
) {
    companion object {
        fun open(stream: InputStream): Seeds {
            val seeds = stream.bufferedReader().use { it.readText() }.split("\n")
            return Seeds(seeds)
        }
    }

    override fun toString(): String = seeds.joinToString(",")
}
