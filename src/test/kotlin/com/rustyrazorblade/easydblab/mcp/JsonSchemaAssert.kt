package com.rustyrazorblade.easydblab.mcp

import kotlinx.serialization.json.JsonElement
import org.assertj.core.api.AbstractAssert

/**
 * Custom AssertJ assertion for JsonSchema objects.
 */
class JsonSchemaAssert(
    actual: JsonSchema,
) : AbstractAssert<JsonSchemaAssert, JsonSchema>(actual, JsonSchemaAssert::class.java) {
    companion object {
        fun assertThat(actual: JsonSchema): JsonSchemaAssert = JsonSchemaAssert(actual)
    }

    fun isObjectType(): JsonSchemaAssert {
        isNotNull
        if (actual.type != "object") {
            failWithMessage("Expected schema type to be <object> but was <%s>", actual.type)
        }
        return this
    }

    fun hasNoProperties(): JsonSchemaAssert {
        isNotNull
        if (actual.properties.isNotEmpty()) {
            failWithMessage("Expected schema to have no properties but had <%s>", actual.properties.keys)
        }
        return this
    }

    fun hasNoRequiredFields(): JsonSchemaAssert {
        isNotNull
        if (actual.required != null) {
            failWithMessage("Expected schema to have no required fields but had <%s>", actual.required)
        }
        return this
    }

    fun hasProperty(name: String): JsonSchemaPropertyAssert {
        isNotNull
        if (!actual.properties.containsKey(name)) {
            failWithMessage("Expected schema to have property <%s> but properties were <%s>", name, actual.properties.keys)
        }
        return JsonSchemaPropertyAssert(actual.properties[name]!!, name)
    }

    fun hasProperties(vararg names: String): JsonSchemaAssert {
        isNotNull
        val missing = names.filter { !actual.properties.containsKey(it) }
        if (missing.isNotEmpty()) {
            failWithMessage("Expected schema to have properties <%s> but was missing <%s>", names.toList(), missing)
        }
        return this
    }

    fun hasPropertyCount(count: Int): JsonSchemaAssert {
        isNotNull
        if (actual.properties.size != count) {
            failWithMessage("Expected schema to have <%d> properties but had <%d>", count, actual.properties.size)
        }
        return this
    }

    fun hasRequiredFields(vararg fields: String): JsonSchemaAssert {
        isNotNull
        if (actual.required == null) {
            failWithMessage("Expected schema to have required fields <%s> but required was null", fields.toList())
        }
        val missing = fields.filter { it !in actual.required!! }
        val extra = actual.required!!.filter { it !in fields }
        if (missing.isNotEmpty() || extra.isNotEmpty()) {
            failWithMessage(
                "Expected required fields <%s> but was <%s>",
                fields.toList(),
                actual.required,
            )
        }
        return this
    }

    fun hasRequiredField(field: String): JsonSchemaAssert {
        isNotNull
        if (actual.required == null || field !in actual.required!!) {
            failWithMessage("Expected <%s> to be required but required was <%s>", field, actual.required)
        }
        return this
    }
}

/**
 * Custom AssertJ assertion for JsonSchemaProperty objects.
 */
class JsonSchemaPropertyAssert(
    actual: JsonSchemaProperty,
    private val propertyName: String,
) : AbstractAssert<JsonSchemaPropertyAssert, JsonSchemaProperty>(actual, JsonSchemaPropertyAssert::class.java) {
    fun withType(expectedType: String): JsonSchemaPropertyAssert {
        isNotNull
        if (actual.type != expectedType) {
            failWithMessage(
                "Expected property <%s> to have type <%s> but was <%s>",
                propertyName,
                expectedType,
                actual.type,
            )
        }
        return this
    }

    fun withDescription(expectedDescription: String): JsonSchemaPropertyAssert {
        isNotNull
        if (actual.description != expectedDescription) {
            failWithMessage(
                "Expected property <%s> to have description <%s> but was <%s>",
                propertyName,
                expectedDescription,
                actual.description,
            )
        }
        return this
    }

    fun withEnumValues(vararg values: String): JsonSchemaPropertyAssert {
        isNotNull
        if (actual.enum == null) {
            failWithMessage("Expected property <%s> to have enum values but enum was null", propertyName)
        }
        val actualValues = actual.enum!!
        if (actualValues != values.toList()) {
            failWithMessage(
                "Expected property <%s> to have enum values <%s> but was <%s>",
                propertyName,
                values.toList(),
                actualValues,
            )
        }
        return this
    }

    fun withDefault(expectedDefault: JsonElement): JsonSchemaPropertyAssert {
        isNotNull
        if (actual.default != expectedDefault) {
            failWithMessage(
                "Expected property <%s> to have default <%s> but was <%s>",
                propertyName,
                expectedDefault,
                actual.default,
            )
        }
        return this
    }

    fun withNoDefault(): JsonSchemaPropertyAssert {
        isNotNull
        if (actual.default != null) {
            failWithMessage(
                "Expected property <%s> to have no default but was <%s>",
                propertyName,
                actual.default,
            )
        }
        return this
    }
}

/**
 * Entry point for JsonSchema assertions.
 */
fun assertThatSchema(actual: JsonSchema): JsonSchemaAssert = JsonSchemaAssert.assertThat(actual)
