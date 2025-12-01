package com.rustyrazorblade.easydblab.commands.converters

import picocli.CommandLine.ITypeConverter

/**
 * PicoCLI converter for availability zone parameter values.
 *
 * Converts input strings to a list of single-letter availability zone identifiers.
 * Supports flexible input formats:
 * - "abc" -> [a, b, c]
 * - "a,b,c" -> [a, b, c]
 * - "a b , c" -> [a, b, c]
 *
 * Only lowercase letters a-z are extracted as valid AZ identifiers.
 */
class PicoAZConverter : ITypeConverter<List<String>> {
    override fun convert(value: String): List<String> {
        val azPattern = "[a-z]".toRegex()
        return value.split("").filter { it.matches(azPattern) }
    }
}
