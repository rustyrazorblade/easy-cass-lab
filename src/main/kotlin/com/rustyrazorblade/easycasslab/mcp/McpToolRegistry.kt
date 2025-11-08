package com.rustyrazorblade.easycasslab.mcp

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import com.beust.jcommander.ParametersDelegate
import com.rustyrazorblade.easycasslab.Command
import com.rustyrazorblade.easycasslab.CommandLineParser
import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.annotations.McpCommand
import com.rustyrazorblade.easycasslab.commands.ICommand
import com.rustyrazorblade.easycasslab.output.OutputHandler
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
import kotlin.getValue
import kotlin.reflect.KProperty1
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaField

/** Registry that manages easy-cass-lab commands as MCP tools. */
open class McpToolRegistry(private val context: Context) : KoinComponent {
    val outputHandler: OutputHandler by inject()

    companion object {
        private val log = KotlinLogging.logger {}
    }

    data class ToolInfo(
        val name: String,
        val description: String,
        val inputSchema: JsonObject,
        val command: Command,
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

        return parser.commands
            .filter { command ->
                // Only include commands with @McpCommand annotation
                command.command::class.java.isAnnotationPresent(McpCommand::class.java)
            }
            .map { command -> createToolInfo(command) }
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

        // Create a fresh command instance to avoid state retention
        // Create an MCP context for command execution
        val mcpContext = Context.forMcp(context.easycasslabUserDirectory)
        val freshCommand =
            try {
                // Try constructor with Context parameter first
                tool.command.command::class
                    .java
                    .getDeclaredConstructor(Context::class.java)
                    .newInstance(mcpContext)
            } catch (e: NoSuchMethodException) {
                // Fallback to no-arg constructor (shouldn't happen anymore)
                try {
                    tool.command.command::class.java.getDeclaredConstructor().newInstance()
                } catch (e2: Exception) {
                    // If we can't create a fresh instance, fall back to the original
                    log.warn {
                        "Could not create fresh command instance for ${tool.name}: ${e2.message}"
                    }
                    tool.command.command
                }
            } catch (e: Exception) {
                // If we can't create a fresh instance, fall back to the original
                log.warn {
                    "Could not create fresh command instance for ${tool.name}: ${e.message}"
                }
                tool.command.command
            }

        // Map JSON arguments to command parameters
        arguments?.let {
            log.debug { "Mapping arguments to command: $it" }
            mapArgumentsToCommand(freshCommand, it)
        } ?: log.debug { "No arguments to map (arguments is null)" }

        try {
            // Stream start message via outputHandler
            outputHandler.handleMessage("Starting execution of tool: $name")

            // Execute the command
            freshCommand.executeAll()

            // Stream completion message
            outputHandler.handleMessage("Tool '$name' completed successfully")

            return ToolResult(
                content = listOf("Tool executed successfully"),
            )
        } catch (e: Exception) {
            log.error(e) { "Error executing command ${tool.name}" }

            // Stream error message
            outputHandler.handleError("Tool '$name' failed: ${e.message}", e)

            return ToolResult(
                content = listOf("Error executing command: ${e.message}"),
                isError = true,
            )
        }
    }

    private fun createToolInfo(command: Command): ToolInfo {
        val description = extractDescription(command.command)
        val schema = generateSchema(command.command)
        log.info { "Creating tool info for $description : $schema" }

        return ToolInfo(
            name = command.name,
            description = description,
            inputSchema = schema,
            command = command,
        )
    }

    private fun extractDescription(command: ICommand): String {
        val parametersAnnotation = command::class.findAnnotation<Parameters>()
        return parametersAnnotation?.commandDescription ?: "No description available"
    }

    fun generateSchema(command: ICommand): JsonObject {
        val properties = mutableMapOf<String, JsonElement>()
        val requiredFields = mutableListOf<String>()

        command::class.memberProperties.forEach { property ->
            processProperty(property, command, properties, requiredFields)
        }

        return buildJsonObject { properties.forEach { (key, value) -> put(key, value) } }
    }

    private fun processProperty(
        property: KProperty1<out ICommand, *>,
        command: ICommand,
        properties: MutableMap<String, JsonElement>,
        requiredFields: MutableList<String>,
    ) {
        val javaField = property.javaField ?: return

        processParameterAnnotation(javaField, property.name, command, properties, requiredFields)
        processParametersDelegateAnnotation(javaField, command, properties, requiredFields)
    }

    private fun processParameterAnnotation(
        javaField: java.lang.reflect.Field,
        fieldName: String,
        command: ICommand,
        properties: MutableMap<String, JsonElement>,
        requiredFields: MutableList<String>,
    ) {
        val paramAnnotation = javaField.getAnnotation(Parameter::class.java) ?: return

        if (paramAnnotation.required) {
            requiredFields.add(fieldName)
        }

        properties[fieldName] = buildPropertySchema(javaField, fieldName, paramAnnotation, command)
    }

    private fun buildPropertySchema(
        javaField: java.lang.reflect.Field,
        fieldName: String,
        paramAnnotation: Parameter,
        command: ICommand,
    ): JsonObject =
        buildJsonObject {
            put("type", determineJsonType(javaField.type))
            put("description", getFieldDescription(paramAnnotation, fieldName))

            if (javaField.type.isEnum) {
                putJsonArray("enum") { addEnumValues(javaField.type) }
            }

            addDefaultValue(javaField, command)
        }

    private fun getFieldDescription(
        paramAnnotation: Parameter,
        fieldName: String,
    ): String = paramAnnotation.description?.takeIf { it.isNotEmpty() } ?: "Parameter: $fieldName"

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

    private fun JsonObjectBuilder.addDefaultValue(
        javaField: java.lang.reflect.Field,
        command: ICommand,
    ) {
        javaField.isAccessible = true
        val defaultValue = javaField.get(command)

        when (defaultValue) {
            is Boolean -> put("default", defaultValue)
            is Number -> put("default", defaultValue)
            is String -> if (defaultValue.isNotEmpty()) put("default", defaultValue)
            is Enum<*> -> put("default", getEnumStringValue(defaultValue))
            null -> {} // No default
            else -> {} // Complex type, skip default
        }
    }

    private fun processParametersDelegateAnnotation(
        javaField: java.lang.reflect.Field,
        command: ICommand,
        properties: MutableMap<String, JsonElement>,
        requiredFields: MutableList<String>,
    ) {
        val delegateAnnotation = javaField.getAnnotation(ParametersDelegate::class.java) ?: return

        try {
            javaField.isAccessible = true
            val delegateObject = javaField.get(command) ?: return
            scanDelegateForParameters(delegateObject, properties, requiredFields)
        } catch (e: Exception) {
            log.warn { "Unable to process delegate: ${e.message}" }
        }
    }

    private fun scanDelegateForParameters(
        delegate: Any,
        properties: MutableMap<String, JsonElement>,
        requiredFields: MutableList<String>,
    ) {
        delegate::class.memberProperties.forEach { property ->
            processDelegateProperty(property, properties, requiredFields)
        }
    }

    private fun processDelegateProperty(
        property: KProperty1<out Any, *>,
        properties: MutableMap<String, JsonElement>,
        requiredFields: MutableList<String>,
    ) {
        val javaField = property.javaField ?: return
        val paramAnnotation = javaField.getAnnotation(Parameter::class.java) ?: return

        val fieldName = property.name

        if (paramAnnotation.required) {
            requiredFields.add(fieldName)
        }

        properties[fieldName] = buildDelegatePropertySchema(javaField, fieldName, paramAnnotation)
    }

    private fun buildDelegatePropertySchema(
        javaField: java.lang.reflect.Field,
        fieldName: String,
        paramAnnotation: Parameter,
    ): JsonObject =
        buildJsonObject {
            put("type", determineJsonType(javaField.type))
            put("description", getFieldDescription(paramAnnotation, fieldName))
        }

    private fun determineJsonType(type: Class<*>): String {
        return when {
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
    }

    private fun mapArgumentsToCommand(
        command: ICommand,
        arguments: JsonObject,
    ) {
        log.debug { "Mapping arguments to ${command::class.simpleName}" }
        command::class.memberProperties.forEach { property ->
            mapPropertyArgument(property, command, arguments)
            mapDelegateArguments(property, command, arguments)
        }
    }

    private fun mapPropertyArgument(
        property: KProperty1<out ICommand, *>,
        command: ICommand,
        arguments: JsonObject,
    ) {
        val javaField = property.javaField ?: return
        val paramAnnotation = javaField.getAnnotation(Parameter::class.java) ?: return

        val fieldName = property.name
        val value = arguments[fieldName]

        value?.takeIf { it !is JsonNull }?.let {
            log.debug { "Setting field '$fieldName'" }
            setFieldValue(command, javaField, it)
        } ?: log.debug { "Skipping field '$fieldName' (null)" }
    }

    private fun mapDelegateArguments(
        property: KProperty1<out ICommand, *>,
        command: ICommand,
        arguments: JsonObject,
    ) {
        val javaField = property.javaField ?: return
        val delegateAnnotation = javaField.getAnnotation(ParametersDelegate::class.java) ?: return

        try {
            javaField.isAccessible = true
            val delegateObject = javaField.get(command)

            delegateObject?.let {
                log.debug { "Mapping arguments to delegate ${it::class.simpleName}" }
                mapArgumentsToDelegate(it, arguments)
            } ?: log.debug { "Delegate object is null, skipping" }
        } catch (e: Exception) {
            log.warn { "Unable to process delegate: ${e.message}" }
        }
    }

    private fun mapArgumentsToDelegate(
        delegate: Any,
        arguments: JsonObject,
    ) {
        log.debug { "Mapping arguments to delegate ${delegate::class.simpleName}" }
        delegate::class.memberProperties.forEach { property ->
            mapDelegatePropertyArgument(property, delegate, arguments)
        }
    }

    private fun mapDelegatePropertyArgument(
        property: KProperty1<out Any, *>,
        delegate: Any,
        arguments: JsonObject,
    ) {
        val javaField = property.javaField ?: return
        val paramAnnotation = javaField.getAnnotation(Parameter::class.java) ?: return

        val fieldName = property.name
        val value = arguments[fieldName]

        value?.takeIf { it !is JsonNull }?.let {
            log.debug { "Setting delegate field '$fieldName'" }
            setFieldValue(delegate, javaField, it)
        } ?: log.debug { "Skipping delegate field '$fieldName' (null)" }
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
