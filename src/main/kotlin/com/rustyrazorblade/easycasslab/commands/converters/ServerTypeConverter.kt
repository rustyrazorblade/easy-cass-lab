package com.rustyrazorblade.easycasslab.commands.converters

import com.beust.jcommander.IStringConverter
import com.rustyrazorblade.easycasslab.configuration.ServerType

/**
 * JCommander converter for ServerType parameter values.
 *
 * Converts case-insensitive string input to ServerType enum values.
 * Supports: "cassandra", "stress", "control" (case-insensitive).
 *
 * @throws IllegalArgumentException if the input is null or not a valid server type
 */
class ServerTypeConverter : IStringConverter<ServerType> {
    override fun convert(value: String?): ServerType =
        when (value?.lowercase()?.trim()) {
            "cassandra" -> ServerType.Cassandra
            "stress" -> ServerType.Stress
            "control" -> ServerType.Control
            null -> throw IllegalArgumentException("Server type cannot be null")
            else -> throw IllegalArgumentException(
                "Invalid server type: $value. Must be cassandra, stress, or control",
            )
        }
}
