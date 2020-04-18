package com.thelastpickle.tlpcluster.configuration.jvm

import java.io.InputStream
import java.util.*
import java.util.stream.Collectors.toList

/**
 * Helper class for manipulating the jvm options such as heap size and other flags
 * Examples:
 * -XX:Setting
 * -XX:+Setting
 * -Xmx16G # example of 16GB heap
 * -ea # enable assertions
 * -da:net.openhft...
 * -Dcassandra.maxHintTTL=max_hint_ttl_in_seconds
 * -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=1414
 * -XX:-UseBiasedLocking
 */
class Configuration(val options: List<JvmOption>) {

    enum class Result {
        ADD, REPLACE, UNKNOWN
    }


    companion object {
        fun fromInputStream(buffer: InputStream) : Configuration {
            val lines = buffer.bufferedReader().lines().collect(toList())
            val options = lines.map { JvmOption.parse(it)  }
            return Configuration(options)
        }
    }

    /**
     * Set the entire flag
     * For example, -Xmn2g
     * or -Dcassandra.available_processors=6
     */
    fun setOption(flag: String) : Result {


        return Result.UNKNOWN
    }

    /**
     * returns the line (for now) of the given option
     * Probably should make a nice type for it
     * Examples: -Xmx
     */
    fun get(flag: String) : Optional<String> {
        return Optional.empty()
    }

    fun length() = options.size


}