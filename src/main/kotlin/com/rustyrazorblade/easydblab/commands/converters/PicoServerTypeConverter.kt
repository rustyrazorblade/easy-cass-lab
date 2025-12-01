package com.rustyrazorblade.easydblab.commands.converters

import com.rustyrazorblade.easydblab.configuration.ServerType
import picocli.CommandLine.ITypeConverter
import picocli.CommandLine.TypeConversionException

/**
 * PicoCLI converter for ServerType parameter values.
 *
 * Converts case-insensitive string input to ServerType enum values.
 * Supports: "cassandra", "stress", "control" (case-insensitive).
 *
 * @throws TypeConversionException if the input is not a valid server type
 */
class PicoServerTypeConverter : ITypeConverter<ServerType> {
    override fun convert(value: String): ServerType =
        when (value.lowercase().trim()) {
            "cassandra" -> ServerType.Cassandra
            "stress" -> ServerType.Stress
            "control" -> ServerType.Control
            else -> throw TypeConversionException(
                "Invalid server type: $value. Must be cassandra, stress, or control",
            )
        }
}
