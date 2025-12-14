package com.rustyrazorblade.easydblab.mcp

import com.rustyrazorblade.easydblab.commands.PicoCommand
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import picocli.CommandLine.Mixin
import picocli.CommandLine.Option
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaField

/**
 * Generates JSON Schema from PicoCLI command options.
 *
 * This class generates compliant JSON Schema format from @Option and @Mixin annotations.
 */
class McpSchemaGenerator {
    companion object {
        private val log = KotlinLogging.logger {}
        private val json = Json { encodeDefaults = false }

        fun toJson(schema: JsonSchema): String = json.encodeToString(schema)
    }

    /**
     * Generate a JSON Schema for a PicoCLI command.
     *
     * @param command The command to generate schema for
     * @return JsonSchema object
     */
    fun generateSchema(command: PicoCommand): JsonSchema {
        val properties = mutableMapOf<String, JsonSchemaProperty>()
        val required = mutableListOf<String>()

        collectOptions(command, properties, required)

        return JsonSchema(
            type = "object",
            properties = properties,
            required = required.ifEmpty { null },
        )
    }

    /**
     * Recursively collect @Option fields from an object and its @Mixin fields.
     */
    private fun collectOptions(
        target: Any,
        properties: MutableMap<String, JsonSchemaProperty>,
        required: MutableList<String>,
    ) {
        target::class.memberProperties.forEach { property ->
            val javaField = property.javaField ?: return@forEach

            // Process @Option annotations
            javaField.getAnnotation(Option::class.java)?.let { option ->
                val schema = buildPropertySchema(javaField.type, option, javaField, target)
                properties[property.name] = schema
                if (option.required) {
                    required.add(property.name)
                }
            }

            // Process @Mixin annotations (recursively collect nested options)
            javaField.getAnnotation(Mixin::class.java)?.let {
                getMixinObject(javaField, target)?.let { mixinObj ->
                    collectOptions(mixinObj, properties, required)
                }
            }
        }
    }

    @Suppress("SwallowedException")
    private fun getMixinObject(
        mixinField: java.lang.reflect.Field,
        target: Any,
    ): Any? =
        try {
            mixinField.isAccessible = true
            mixinField.get(target)
        } catch (e: Exception) {
            log.warn { "Unable to access mixin ${mixinField.name}: ${e.message}" }
            null
        }

    private fun buildPropertySchema(
        type: Class<*>,
        option: Option,
        field: java.lang.reflect.Field,
        target: Any,
    ): JsonSchemaProperty {
        val jsonType = determineJsonType(type)
        val description = option.description.firstOrNull() ?: "Parameter: ${field.name}"

        val enumValues =
            if (type.isEnum) {
                type.enumConstants.map { getEnumStringValue(it) }
            } else {
                null
            }

        val defaultValue = getDefaultValue(field, target)

        return JsonSchemaProperty.of(
            type = jsonType,
            description = description,
            enumValues = enumValues,
            default = defaultValue,
        )
    }

    private fun determineJsonType(type: Class<*>): JsonSchemaType =
        when {
            type.isEnum -> JsonSchemaType.STRING
            type == String::class.java -> JsonSchemaType.STRING
            type == Int::class.java || type == Integer::class.java -> JsonSchemaType.INTEGER
            type == Long::class.java || type == java.lang.Long::class.java -> JsonSchemaType.INTEGER
            type == Double::class.java || type == java.lang.Double::class.java -> JsonSchemaType.NUMBER
            type == Float::class.java || type == java.lang.Float::class.java -> JsonSchemaType.NUMBER
            type == Boolean::class.java || type == java.lang.Boolean::class.java -> JsonSchemaType.BOOLEAN
            List::class.java.isAssignableFrom(type) -> JsonSchemaType.ARRAY
            else -> JsonSchemaType.STRING
        }

    @Suppress("SwallowedException")
    private fun getDefaultValue(
        field: java.lang.reflect.Field,
        target: Any,
    ): JsonElement? =
        try {
            field.isAccessible = true
            when (val value = field.get(target)) {
                null -> null
                is String -> if (value.isNotEmpty()) JsonPrimitive(value) else null
                is Boolean -> JsonPrimitive(value)
                is Int -> JsonPrimitive(value)
                is Long -> JsonPrimitive(value)
                is Double -> JsonPrimitive(value)
                is Float -> JsonPrimitive(value)
                is Number -> JsonPrimitive(value.toDouble())
                is Enum<*> -> JsonPrimitive(getEnumStringValue(value))
                else -> null
            }
        } catch (_: Exception) {
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
}

/**
 * Data class representing a JSON Schema object.
 */
@Serializable
data class JsonSchema(
    val type: String,
    val properties: Map<String, JsonSchemaProperty>,
    val required: List<String>? = null,
) {
    fun toJson(): String = McpSchemaGenerator.toJson(this)
}

/**
 * Data class representing a JSON Schema property definition.
 */
@Serializable
data class JsonSchemaProperty(
    val type: String,
    val description: String,
    val enum: List<String>? = null,
    val default: JsonElement? = null,
) {
    companion object {
        fun of(
            type: JsonSchemaType,
            description: String,
            enumValues: List<String>? = null,
            default: JsonElement? = null,
        ): JsonSchemaProperty =
            JsonSchemaProperty(
                type = type.value,
                description = description,
                enum = enumValues,
                default = default,
            )
    }
}

/**
 * Type-safe representation of JSON Schema types.
 */
enum class JsonSchemaType(
    val value: String,
) {
    STRING("string"),
    INTEGER("integer"),
    NUMBER("number"),
    BOOLEAN("boolean"),
    ARRAY("array"),
    OBJECT("object"),
}
