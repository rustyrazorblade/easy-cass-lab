package com.rustyrazorblade.easydblab.mcp

import com.rustyrazorblade.easydblab.CommandLineParser
import com.rustyrazorblade.easydblab.Context
import com.rustyrazorblade.easydblab.PicoCommandEntry
import com.rustyrazorblade.easydblab.annotations.McpCommand
import com.rustyrazorblade.easydblab.commands.PicoCommand
import com.rustyrazorblade.easydblab.output.OutputHandler
import com.rustyrazorblade.easydblab.services.CommandExecutor
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.JsonArrayBuilder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import picocli.CommandLine.Mixin
import picocli.CommandLine.Option
import kotlin.getValue
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaField
import picocli.CommandLine.Command as PicoCommandAnnotation

/** Registry that manages easy-db-lab commands as MCP tools. */
open class McpToolRegistry(
    private val context: Context,
) : KoinComponent {
    val outputHandler: OutputHandler by inject()
    private val commandExecutor: CommandExecutor by inject()

    companion object {
        private val log = KotlinLogging.logger {}
    }

    data class ToolInfo(
        val name: String,
        val description: String,
        val inputSchema: JsonObject,
        val entry: PicoCommandEntry,
    )

    data class ToolResult(
        val content: List<String>,
        val isError: Boolean = false,
    )

    /**
     * Get all available tools from the command registry. Only includes commands annotated with
     * @McpCommand.
     */
    open fun getTools(): List<ToolInfo> {
        val parser = CommandLineParser(context)

        // PicoCLI commands with @McpCommand annotation
        return parser.picoCommands
            .filter { entry ->
                // Create a temporary instance to check for annotation
                val tempCommand = entry.factory()
                tempCommand::class.java.isAnnotationPresent(McpCommand::class.java)
            }.map { entry -> createToolInfoFromPico(entry) }
    }

    /** Execute a tool by name with the given arguments. */
    fun executeTool(
        name: String,
        arguments: JsonObject?,
    ): ToolResult {
        log.debug { "executeTool called with name='$name', arguments=$arguments" }
        val tool =
            getTools().find { it.name == name }
                ?: return ToolResult(
                    content = listOf("Tool not found: $name"),
                    isError = true,
                )

        return executeAndCaptureResult(name) {
            // Use CommandExecutor for full lifecycle (requirements, execution, backup)
            commandExecutor.execute {
                // Create a fresh command instance using the factory
                val freshCommand = tool.entry.factory()

                // Map JSON arguments to command parameters
                arguments?.let {
                    log.debug { "Mapping arguments to PicoCLI command: $it" }
                    mapArgumentsToPicoCommand(freshCommand, it)
                } ?: log.debug { "No arguments to map (arguments is null)" }

                freshCommand
            }
        }
    }

    /** Common execution wrapper that handles output and errors. */
    private fun executeAndCaptureResult(
        name: String,
        action: () -> Unit,
    ): ToolResult =
        try {
            outputHandler.handleMessage("Starting execution of tool: $name")
            action()
            outputHandler.handleMessage("Tool '$name' completed successfully")
            ToolResult(content = listOf("Tool executed successfully"))
        } catch (e: Exception) {
            log.error(e) { "Error executing command $name" }
            outputHandler.handleError("Tool '$name' failed: ${e.message}", e)
            ToolResult(content = listOf("Error executing command: ${e.message}"), isError = true)
        }

    /** Create ToolInfo from a PicoCLI command entry. */
    private fun createToolInfoFromPico(entry: PicoCommandEntry): ToolInfo {
        // Create a temporary instance to extract metadata
        val tempCommand = entry.factory()
        val description = extractPicoDescription(tempCommand)
        val schema = generatePicoSchema(tempCommand)
        log.info { "Creating PicoCLI tool info for $description : $schema" }

        return ToolInfo(
            name = entry.name,
            description = description,
            inputSchema = schema,
            entry = entry,
        )
    }

    /** Extract description from PicoCLI @Command annotation. */
    private fun extractPicoDescription(command: PicoCommand): String {
        val commandAnnotation = command::class.java.getAnnotation(PicoCommandAnnotation::class.java)
        return commandAnnotation?.description?.firstOrNull() ?: "No description available"
    }

    /** Generate JSON schema from PicoCLI @Option and @Mixin annotations. */
    fun generatePicoSchema(command: PicoCommand): JsonObject {
        val properties = mutableMapOf<String, JsonElement>()
        val requiredFields = mutableListOf<String>()

        command::class.memberProperties.forEach { property ->
            processPicoProperty(property, command, properties, requiredFields)
        }

        return buildJsonObject { properties.forEach { (key, value) -> put(key, value) } }
    }

    private fun processPicoProperty(
        property: KProperty1<out PicoCommand, *>,
        command: PicoCommand,
        properties: MutableMap<String, JsonElement>,
        requiredFields: MutableList<String>,
    ) {
        val javaField = property.javaField ?: return

        processOptionAnnotation(javaField, property.name, command, properties, requiredFields)
        processMixinAnnotation(javaField, command, properties, requiredFields)
    }

    // ==================== PicoCLI @Option Processing ====================

    private fun processOptionAnnotation(
        javaField: java.lang.reflect.Field,
        fieldName: String,
        command: PicoCommand,
        properties: MutableMap<String, JsonElement>,
        requiredFields: MutableList<String>,
    ) {
        val optionAnnotation = javaField.getAnnotation(Option::class.java) ?: return

        if (optionAnnotation.required) {
            requiredFields.add(fieldName)
        }

        properties[fieldName] = buildPicoOptionSchema(javaField, fieldName, optionAnnotation, command)
    }

    private fun buildPicoOptionSchema(
        javaField: java.lang.reflect.Field,
        fieldName: String,
        optionAnnotation: Option,
        command: PicoCommand,
    ): JsonObject =
        buildJsonObject {
            put("type", determineJsonType(javaField.type))
            put("description", getPicoOptionDescription(optionAnnotation, fieldName))

            if (javaField.type.isEnum) {
                putJsonArray("enum") { addEnumValues(javaField.type) }
            }

            addDefaultValueFromAny(javaField, command)
        }

    private fun getPicoOptionDescription(
        optionAnnotation: Option,
        fieldName: String,
    ): String {
        val descriptions = optionAnnotation.description
        return if (descriptions.isNotEmpty() && descriptions.first().isNotEmpty()) {
            descriptions.first()
        } else {
            "Parameter: $fieldName"
        }
    }

    // ==================== PicoCLI @Mixin Processing ====================

    private fun processMixinAnnotation(
        javaField: java.lang.reflect.Field,
        command: PicoCommand,
        properties: MutableMap<String, JsonElement>,
        requiredFields: MutableList<String>,
    ) {
        javaField.getAnnotation(Mixin::class.java) ?: return

        try {
            javaField.isAccessible = true
            val mixinObject = javaField.get(command) ?: return
            scanMixinForOptions(mixinObject, properties, requiredFields)
        } catch (e: Exception) {
            log.warn { "Unable to process mixin: ${e.message}" }
        }
    }

    private fun scanMixinForOptions(
        mixin: Any,
        properties: MutableMap<String, JsonElement>,
        requiredFields: MutableList<String>,
    ) {
        mixin::class.memberProperties.forEach { property ->
            processMixinProperty(property, mixin, properties, requiredFields)
        }
    }

    private fun processMixinProperty(
        property: KProperty1<out Any, *>,
        mixin: Any,
        properties: MutableMap<String, JsonElement>,
        requiredFields: MutableList<String>,
    ) {
        val javaField = property.javaField ?: return
        val optionAnnotation = javaField.getAnnotation(Option::class.java) ?: return

        val fieldName = property.name

        if (optionAnnotation.required) {
            requiredFields.add(fieldName)
        }

        properties[fieldName] = buildMixinPropertySchema(javaField, fieldName, optionAnnotation, mixin)
    }

    private fun buildMixinPropertySchema(
        javaField: java.lang.reflect.Field,
        fieldName: String,
        optionAnnotation: Option,
        mixin: Any,
    ): JsonObject =
        buildJsonObject {
            put("type", determineJsonType(javaField.type))
            put("description", getPicoOptionDescription(optionAnnotation, fieldName))

            if (javaField.type.isEnum) {
                putJsonArray("enum") { addEnumValues(javaField.type) }
            }

            addDefaultValueFromAny(javaField, mixin)
        }

    /** Add default value to JSON schema from any object (not just ICommand). */
    private fun JsonObjectBuilder.addDefaultValueFromAny(
        javaField: java.lang.reflect.Field,
        target: Any,
    ) {
        javaField.isAccessible = true
        val defaultValue = javaField.get(target)

        when (defaultValue) {
            is Boolean -> put("default", defaultValue)
            is Number -> put("default", defaultValue)
            is String -> if (defaultValue.isNotEmpty()) put("default", defaultValue)
            is Enum<*> -> put("default", getEnumStringValue(defaultValue))
            null -> {} // No default
            else -> {} // Complex type, skip default
        }
    }

    private fun JsonArrayBuilder.addEnumValues(enumType: Class<*>) {
        enumType.enumConstants.forEach { enumValue ->
            add(getEnumStringValue(enumValue))
        }
    }

    private fun getEnumStringValue(enumValue: Any): String =
        if (enumValue is Enum<*>) {
            try {
                val typeMethod = enumValue.javaClass.getMethod("getType")
                typeMethod.invoke(enumValue) as String
            } catch (e: Exception) {
                enumValue.name.lowercase()
            }
        } else {
            enumValue.toString()
        }

    private fun determineJsonType(type: Class<*>): String =
        when {
            type.isEnum -> "string" // Enums are strings with constraints
            type == String::class.java -> "string"
            type == Int::class.java ||
                type == Integer::class.java ||
                type == Long::class.java ||
                type == java.lang.Long::class.java ||
                type == Double::class.java ||
                type == java.lang.Double::class.java ||
                type == Float::class.java ||
                type == java.lang.Float::class.java -> "number"
            type == Boolean::class.java || type == java.lang.Boolean::class.java -> "boolean"
            else -> "string" // Default to string for unknown types
        }

    // ==================== PicoCLI Argument Mapping ====================

    private fun mapArgumentsToPicoCommand(
        command: PicoCommand,
        arguments: JsonObject,
    ) {
        log.debug { "Mapping arguments to PicoCLI command ${command::class.simpleName}" }
        command::class.memberProperties.forEach { property ->
            mapPicoOptionArgument(property, command, arguments)
            mapPicoMixinArguments(property, command, arguments)
        }
    }

    private fun mapPicoOptionArgument(
        property: KProperty1<out PicoCommand, *>,
        command: PicoCommand,
        arguments: JsonObject,
    ) {
        val javaField = property.javaField ?: return
        javaField.getAnnotation(Option::class.java) ?: return

        val fieldName = property.name
        val value = arguments[fieldName]

        value?.takeIf { it !is JsonNull }?.let {
            log.debug { "Setting PicoCLI option '$fieldName'" }
            setFieldValue(command, javaField, it)
        } ?: log.debug { "Skipping option '$fieldName' (null)" }
    }

    private fun mapPicoMixinArguments(
        property: KProperty1<out PicoCommand, *>,
        command: PicoCommand,
        arguments: JsonObject,
    ) {
        val javaField = property.javaField ?: return
        javaField.getAnnotation(Mixin::class.java) ?: return

        try {
            javaField.isAccessible = true
            val mixinObject = javaField.get(command)

            mixinObject?.let {
                log.debug { "Mapping arguments to PicoCLI mixin ${it::class.simpleName}" }
                mapArgumentsToPicoMixin(it, arguments)
            } ?: log.debug { "Mixin object is null, skipping" }
        } catch (e: Exception) {
            log.warn { "Unable to process mixin: ${e.message}" }
        }
    }

    private fun mapArgumentsToPicoMixin(
        mixin: Any,
        arguments: JsonObject,
    ) {
        log.debug { "Mapping arguments to PicoCLI mixin ${mixin::class.simpleName}" }
        mixin::class.memberProperties.forEach { property ->
            mapPicoMixinPropertyArgument(property, mixin, arguments)
        }
    }

    private fun mapPicoMixinPropertyArgument(
        property: KProperty1<out Any, *>,
        mixin: Any,
        arguments: JsonObject,
    ) {
        val javaField = property.javaField ?: return
        javaField.getAnnotation(Option::class.java) ?: return

        val fieldName = property.name
        val value = arguments[fieldName]

        value?.takeIf { it !is JsonNull }?.let {
            log.debug { "Setting PicoCLI mixin field '$fieldName'" }
            setFieldValue(mixin, javaField, it)
        } ?: log.debug { "Skipping mixin field '$fieldName' (null)" }
    }

    private fun setFieldValue(
        target: Any,
        field: java.lang.reflect.Field,
        value: JsonElement,
    ) {
        try {
            field.isAccessible = true
            when {
                field.type.isEnum -> setEnumFieldValue(field, target, value)
                field.type == String::class.java -> field.set(target, value.jsonPrimitive.content)
                isIntType(field.type) -> field.set(target, value.jsonPrimitive.content.toInt())
                isLongType(field.type) -> field.set(target, value.jsonPrimitive.content.toLong())
                isDoubleType(field.type) -> field.set(target, value.jsonPrimitive.content.toDouble())
                isFloatType(field.type) -> field.set(target, value.jsonPrimitive.content.toFloat())
                isBooleanType(field.type) -> field.set(target, value.jsonPrimitive.content.toBoolean())
            }
            log.debug { "Set field '${field.name}' on ${target::class.simpleName}" }
        } catch (e: Exception) {
            log.warn {
                "Unable to set field '${field.name}' on ${target::class.simpleName}: ${e.message}"
            }
        }
    }

    private fun setEnumFieldValue(
        field: java.lang.reflect.Field,
        target: Any,
        value: JsonElement,
    ) {
        val enumString = value.jsonPrimitive.content
        val enumValue = findMatchingEnumValue(field.type, enumString)

        enumValue?.let {
            field.set(target, it)
        } ?: log.warn { "Unable to find enum value '$enumString' for field '${field.name}'" }
    }

    private fun findMatchingEnumValue(
        enumType: Class<*>,
        enumString: String,
    ): Any? =
        enumType.enumConstants.firstOrNull { enumConstant ->
            matchesEnumValue(enumConstant, enumString)
        }

    private fun matchesEnumValue(
        enumConstant: Any,
        enumString: String,
    ): Boolean =
        if (enumConstant is Enum<*>) {
            matchesEnumByTypeOrName(enumConstant, enumString)
        } else {
            enumConstant.toString() == enumString
        }

    private fun matchesEnumByTypeOrName(
        enumConstant: Enum<*>,
        enumString: String,
    ): Boolean =
        try {
            val typeMethod = enumConstant.javaClass.getMethod("getType")
            val typeValue = typeMethod.invoke(enumConstant) as String
            typeValue == enumString
        } catch (e: Exception) {
            enumConstant.name.equals(enumString, ignoreCase = true)
        }

    private fun isIntType(type: Class<*>): Boolean = type == Int::class.java || type == Integer::class.java

    private fun isLongType(type: Class<*>): Boolean = type == Long::class.java || type == java.lang.Long::class.java

    private fun isDoubleType(type: Class<*>): Boolean = type == Double::class.java || type == java.lang.Double::class.java

    private fun isFloatType(type: Class<*>): Boolean = type == Float::class.java || type == java.lang.Float::class.java

    private fun isBooleanType(type: Class<*>): Boolean = type == Boolean::class.java || type == java.lang.Boolean::class.java
}
