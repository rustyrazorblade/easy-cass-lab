package com.rustyrazorblade.easycasslab.commands.converters

import com.beust.jcommander.IStringConverter

/**
 * The AZConverter is meant to be crazy simple and if a person is guessing, they'll just guess right
 * If somebody were to enter any of the following after --az, it should parse:
 *
 * abc
 * a,b,c
 * "a    b    , c"
 *
 * For now we don't care about regions - if a user enters this:
 *
 * us-west-2b
 *
 * It'll just fail.  We might follow up to fix this is we see it's an issue.
 */
class AZConverter : IStringConverter<List<String>> {
    override fun convert(value: String?): List<String> {
        if (value == null) return listOf()

        return value.split("").filter { it.matches("[a-z]".toRegex()) }
    }
}
