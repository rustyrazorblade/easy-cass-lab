package com.rustyrazorblade.easycasslab.configuration

import io.github.oshai.kotlinlogging.KotlinLogging

typealias Alias = String

data class Host(val public: String,
                val private: String,
                val alias: Alias,
                val availabilityZone: String) {

    companion object {
        val hostRegex = """aws_instance\.(\w+)(.(\d+))?""".toRegex()
        val log = KotlinLogging.logger {}

        fun fromTerraformString(str: String, public: String, private: String, availabilityZone: String) : Host {
            val tmp = hostRegex.find(str)!!.groups

            val serverType = tmp[1]?.value.toString()
            val serverNum = (tmp[3]?.value ?: 0).toString()

            log.debug { "Regex find: $tmp" }
            return Host(public, private, serverType + serverNum, availabilityZone)

        }
    }
}

