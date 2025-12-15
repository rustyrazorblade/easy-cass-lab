package com.rustyrazorblade.easydblab.commands.converters

import com.rustyrazorblade.easydblab.configuration.Arch
import picocli.CommandLine.ITypeConverter
import picocli.CommandLine.TypeConversionException

/**
 * PicoCLI converter for Arch parameter values.
 *
 * Converts case-insensitive string input to Arch enum values.
 * Supports: "amd64", "arm64" (case-insensitive).
 *
 * @throws TypeConversionException if the input is not a valid architecture
 */
class PicoArchConverter : ITypeConverter<Arch> {
    override fun convert(value: String): Arch =
        when (value.lowercase().trim()) {
            "amd64" -> Arch.AMD64
            "arm64" -> Arch.ARM64
            else -> throw TypeConversionException(
                "Invalid architecture: $value. Must be amd64 or arm64",
            )
        }
}
