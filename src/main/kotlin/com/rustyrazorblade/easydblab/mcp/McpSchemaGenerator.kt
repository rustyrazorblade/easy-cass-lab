package com.rustyrazorblade.easydblab.mcp

import com.rustyrazorblade.easydblab.commands.PicoCommand
import io.github.oshai.kotlinlogging.KotlinLogging
import picocli.CommandLine.Mixin
import picocli.CommandLine.Option
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaField

/**
 * Generates JSON Schema strings from PicoCLI command options.
 *
 * The Java MCP SDK expects schema as a JSON string, which it parses internally.
 * This class generates compliant JSON Schema format from @Option and @Mixin annotations.
 */
class McpSchemaGenerator {
    companion object {
        private val log = KotlinLogging.logger {}
    }

    /**
     * Generate a JSON Schema string for a PicoCLI command.
     *
     * @param command The command to generate schema for
     * @return JSON Schema as a string
     */
    fun generateSchema(command: PicoCommand): String {
        val properties = mutableMapOf<String, PropertySchema>()
        val required = mutableListOf<String>()

        command::class.memberProperties.forEach { property ->
            val javaField = property.javaField ?: return@forEach

            // Process @Option annotations
            javaField.getAnnotation(Option::class.java)?.let { option ->
                val schema = buildPropertySchema(javaField.type, option, javaField, command)
                properties[property.name] = schema
                if (option.required) {
                    required.add(property.name)
                }
            }

            // Process @Mixin annotations (extract nested options)
            javaField.getAnnotation(Mixin::class.java)?.let {
                processMixin(javaField, command, properties, required)
            }
        }

        return buildSchemaJson(properties, required)
    }

    private fun buildPropertySchema(
        type: Class<*>,
        option: Option,
        field: java.lang.reflect.Field,
        target: Any,
    ): PropertySchema {
        val jsonType = determineJsonType(type)
        val description = option.description.firstOrNull()?.escapeJson() ?: "Parameter: ${field.name}"

        val enumValues =
            if (type.isEnum) {
                type.enumConstants.map { getEnumStringValue(it) }
            } else {
                null
            }

        val defaultValue = getDefaultValue(field, target)

        return PropertySchema(jsonType, description, enumValues, defaultValue)
    }

    private fun processMixin(
        mixinField: java.lang.reflect.Field,
        command: PicoCommand,
        properties: MutableMap<String, PropertySchema>,
        required: MutableList<String>,
    ) {
        try {
            mixinField.isAccessible = true
            val mixinObject = mixinField.get(command) ?: return

            mixinObject::class.memberProperties.forEach { property ->
                val javaField = property.javaField ?: return@forEach

                javaField.getAnnotation(Option::class.java)?.let { option ->
                    val schema = buildPropertySchema(javaField.type, option, javaField, mixinObject)
                    properties[property.name] = schema
                    if (option.required) {
                        required.add(property.name)
                    }
                }
            }
        } catch (e: Exception) {
            log.warn { "Unable to process mixin ${mixinField.name}: ${e.message}" }
        }
    }

    private fun buildSchemaJson(
        properties: Map<String, PropertySchema>,
        required: List<String>,
    ): String {
        if (properties.isEmpty()) {
            return """{"type": "object", "properties": {}}"""
        }

        val propsJson =
            properties.entries.joinToString(",\n        ") { (name, schema) ->
                buildPropertyJson(name, schema)
            }

        val requiredJson =
            if (required.isNotEmpty()) {
                """, "required": [${required.joinToString(", ") { "\"$it\"" }}]"""
            } else {
                ""
            }

        return """{
    "type": "object",
    "properties": {
        $propsJson
    }$requiredJson
}"""
    }

    private fun buildPropertyJson(
        name: String,
        schema: PropertySchema,
    ): String {
        val parts = mutableListOf<String>()
        parts.add(""""type": "${schema.type}"""")
        parts.add(""""description": "${schema.description}"""")

        schema.enumValues?.let { values ->
            parts.add(""""enum": [${values.joinToString(", ") { "\"$it\"" }}]""")
        }

        schema.defaultValue?.let { default ->
            val defaultJson =
                when (default) {
                    is String -> "\"${default.escapeJson()}\""
                    is Number -> default.toString()
                    is Boolean -> default.toString()
                    else -> "\"$default\""
                }
            parts.add(""""default": $defaultJson""")
        }

        return """"$name": {${parts.joinToString(", ")}}"""
    }

    private fun determineJsonType(type: Class<*>): String =
        when {
            type.isEnum -> "string"
            type == String::class.java -> "string"
            type == Int::class.java || type == Integer::class.java -> "integer"
            type == Long::class.java || type == java.lang.Long::class.java -> "integer"
            type == Double::class.java || type == java.lang.Double::class.java -> "number"
            type == Float::class.java || type == java.lang.Float::class.java -> "number"
            type == Boolean::class.java || type == java.lang.Boolean::class.java -> "boolean"
            List::class.java.isAssignableFrom(type) -> "array"
            else -> "string"
        }

    @Suppress("SwallowedException")
    private fun getDefaultValue(
        field: java.lang.reflect.Field,
        target: Any,
    ): Any? =
        try {
            field.isAccessible = true
            when (val value = field.get(target)) {
                null -> null
                is String -> if (value.isNotEmpty()) value else null
                is Boolean -> value
                is Number -> value
                is Enum<*> -> getEnumStringValue(value)
                else -> null
            }
        } catch (e: Exception) {
            null
        }

    @Suppress("SwallowedException")
    private fun getEnumStringValue(enumValue: Any): String =
        if (enumValue is Enum<*>) {
            try {
                // Try to use getType() method if available (common pattern in this codebase)
                val typeMethod = enumValue.javaClass.getMethod("getType")
                typeMethod.invoke(enumValue) as String
            } catch (e: Exception) {
                enumValue.name.lowercase()
            }
        } else {
            enumValue.toString()
        }

    private fun String.escapeJson(): String =
        this
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")

    private data class PropertySchema(
        val type: String,
        val description: String,
        val enumValues: List<String>?,
        val defaultValue: Any?,
    )
}
