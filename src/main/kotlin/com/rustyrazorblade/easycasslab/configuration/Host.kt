package com.rustyrazorblade.easycasslab.configuration

import io.github.oshai.kotlinlogging.KotlinLogging

typealias Alias = String

data class Host(
    val public: String,
    val private: String,
    val alias: Alias,
    val availabilityZone: String,
) {
    companion object {
        private const val DEFAULT_SERVER_NUMBER = 0
        private const val SERVER_TYPE_GROUP_INDEX = 1
        private const val SERVER_NUM_GROUP_INDEX = 3

        val hostRegex = """aws_instance\.(\w+)(.(\d+))?""".toRegex()
        val log = KotlinLogging.logger {}

        fun fromTerraformString(
            str: String,
            public: String,
            private: String,
            availabilityZone: String,
        ): Host {
            val tmp =
                hostRegex.find(str)?.groups
                    ?: throw IllegalArgumentException("Invalid host string format: $str")

            val serverType = tmp[SERVER_TYPE_GROUP_INDEX]?.value.toString()
            val serverNum = (tmp[SERVER_NUM_GROUP_INDEX]?.value ?: DEFAULT_SERVER_NUMBER).toString()

            log.debug { "Regex find: $tmp" }
            return Host(public, private, serverType + serverNum, availabilityZone)
        }
    }
}
