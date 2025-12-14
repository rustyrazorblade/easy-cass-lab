package com.rustyrazorblade.easydblab.mcp

import io.github.oshai.kotlinlogging.KotlinLogging
import java.lang.reflect.Field

/**
 * Utility functions for reflection-based operations on PicoCLI commands.
 */
object ReflectionUtils {
    private val log = KotlinLogging.logger {}

    /**
     * Safely access a mixin field's value from a target object.
     */
    @Suppress("SwallowedException")
    fun getMixinObject(
        mixinField: Field,
        target: Any,
    ): Any? =
        try {
            mixinField.isAccessible = true
            mixinField.get(target)
        } catch (e: Exception) {
            log.warn { "Unable to access mixin ${mixinField.name}: ${e.message}" }
            null
        }
}

/**
 * Utility for converting values to specific types, handling both Kotlin and Java types.
 */
object TypeConverter {
    fun toInt(value: Any): Int =
        when (value) {
            is Number -> value.toInt()
            else -> value.toString().toInt()
        }

    fun toLong(value: Any): Long =
        when (value) {
            is Number -> value.toLong()
            else -> value.toString().toLong()
        }

    fun toDouble(value: Any): Double =
        when (value) {
            is Number -> value.toDouble()
            else -> value.toString().toDouble()
        }

    fun toFloat(value: Any): Float =
        when (value) {
            is Number -> value.toFloat()
            else -> value.toString().toFloat()
        }

    fun toBoolean(value: Any): Boolean =
        when (value) {
            is Boolean -> value
            else -> value.toString().toBoolean()
        }
}

/**
 * Utility for checking Java/Kotlin primitive type compatibility.
 */
object TypeChecker {
    private val intTypes = setOf(Int::class.java, Integer::class.java)
    private val longTypes = setOf(Long::class.java, java.lang.Long::class.java)
    private val doubleTypes = setOf(Double::class.java, java.lang.Double::class.java)
    private val floatTypes = setOf(Float::class.java, java.lang.Float::class.java)
    private val booleanTypes = setOf(Boolean::class.java, java.lang.Boolean::class.java)

    fun isInt(type: Class<*>): Boolean = type in intTypes

    fun isLong(type: Class<*>): Boolean = type in longTypes

    fun isDouble(type: Class<*>): Boolean = type in doubleTypes

    fun isFloat(type: Class<*>): Boolean = type in floatTypes

    fun isBoolean(type: Class<*>): Boolean = type in booleanTypes
}

/**
 * Mapping from Java/Kotlin types to JSON Schema types.
 */
object JsonSchemaTypeMapper {
    private val typeMap: Map<Class<*>, JsonSchemaType> =
        buildMap {
            put(String::class.java, JsonSchemaType.STRING)
            put(Int::class.java, JsonSchemaType.INTEGER)
            put(Integer::class.java, JsonSchemaType.INTEGER)
            put(Long::class.java, JsonSchemaType.INTEGER)
            put(java.lang.Long::class.java, JsonSchemaType.INTEGER)
            put(Double::class.java, JsonSchemaType.NUMBER)
            put(java.lang.Double::class.java, JsonSchemaType.NUMBER)
            put(Float::class.java, JsonSchemaType.NUMBER)
            put(java.lang.Float::class.java, JsonSchemaType.NUMBER)
            put(Boolean::class.java, JsonSchemaType.BOOLEAN)
            put(java.lang.Boolean::class.java, JsonSchemaType.BOOLEAN)
        }

    fun getJsonSchemaType(type: Class<*>): JsonSchemaType =
        when {
            type.isEnum -> JsonSchemaType.STRING
            List::class.java.isAssignableFrom(type) -> JsonSchemaType.ARRAY
            else -> typeMap[type] ?: JsonSchemaType.STRING
        }
}
