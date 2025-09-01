package com.rustyrazorblade.easycasslab.mcp

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import com.beust.jcommander.ParametersDelegate
import com.rustyrazorblade.easycasslab.Command
import com.rustyrazorblade.easycasslab.CommandLineParser
import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.commands.ICommand
import com.rustyrazorblade.easycasslab.output.OutputHandler
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.*
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.getValue
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaField

/**
 * Registry that manages easy-cass-lab commands as MCP tools.
 */
open class McpToolRegistry(private val context: Context) : KoinComponent {
    val outputHandler: OutputHandler by inject()

    companion object {
        private val log = KotlinLogging.logger {}
        
        // Commands that should not be exposed as MCP tools
        private val EXCLUDED_COMMANDS = setOf(
            "mcp",    // This command itself
            "repl",   // Interactive REPL
            "server",  // HTTP server daemon
        )
    }
    
    data class ToolInfo(
        val name: String,
        val description: String,
        val inputSchema: JsonObject,
        val command: Command
    )
    
    data class ToolResult(
        val content: List<String>,
        val isError: Boolean = false
    )
    
    /**
     * Get all available tools from the command registry.
     */
    open fun getTools(): List<ToolInfo> {
        val parser = CommandLineParser(context)
        
        return parser.commands
            .filter { command -> !EXCLUDED_COMMANDS.contains(command.name) }
            .map { command ->
                createToolInfo(command)
            }
    }
    
    /**
     * Execute a tool by name with the given arguments.
     */
    fun executeTool(name: String, arguments: JsonObject?): ToolResult {
        log.info { "executeTool called with name='$name', arguments=$arguments" }
        val tool = getTools().find { it.name == name }
        
        if (tool == null) {
            return ToolResult(
                content = listOf("Tool not found: $name"),
                isError = true
            )
        }

        // Map JSON arguments to command parameters
        if (arguments != null) {
            log.info { "Mapping arguments to command: $arguments" }
            mapArgumentsToCommand(tool.command.command, arguments)
        } else {
            log.info { "No arguments to map (arguments is null)" }
        }

        // Execute the command
        tool.command.command.execute()

        val output = outputHandler.toString()

        return ToolResult(
            content = listOf(output.ifEmpty { "Command completed successfully" })
        )
    }
    
    private fun createToolInfo(command: Command): ToolInfo {
        val description = extractDescription(command.command)
        val schema = generateSchema(command.command)
        log.info { "Creating tool info for $description with $schema" }
        
        return ToolInfo(
            name = command.name,
            description = description,
            inputSchema = schema,
            command = command
        )
    }
    
    private fun extractDescription(command: ICommand): String {
        val parametersAnnotation = command::class.findAnnotation<Parameters>()
        return parametersAnnotation?.commandDescription 
            ?: "No description available"
    }
    
    fun generateSchema(command: ICommand): JsonObject {
        val properties = mutableMapOf<String, JsonElement>()
        val requiredFields = mutableListOf<String>()
        
        // Scan all properties for Parameter annotations
        command::class.memberProperties.forEach { property ->
            val javaField = property.javaField
            if (javaField != null) {
                val paramAnnotation = javaField.getAnnotation(Parameter::class.java)
                if (paramAnnotation != null) {
                    val fieldName = property.name
                    
                    // Check if field is required
                    if (paramAnnotation.required) {
                        requiredFields.add(fieldName)
                    }
                    
                    properties[fieldName] = buildJsonObject {
                        put("type", determineJsonType(javaField.type))
                        // Use description if available, otherwise use field name as fallback
                        val desc = paramAnnotation.description?.takeIf { it.isNotEmpty() } 
                            ?: "Parameter: $fieldName"
                        put("description", desc)
                        
                        // Add enum constraints if this is an enum type
                        if (javaField.type.isEnum) {
                            putJsonArray("enum") {
                                javaField.type.enumConstants.forEach { enumValue ->
                                    // Get the string representation of the enum
                                    val enumString = if (enumValue is Enum<*>) {
                                        // Try to get the 'type' property if it exists (like Arch.type)
                                        try {
                                            val typeMethod = enumValue.javaClass.getMethod("getType")
                                            typeMethod.invoke(enumValue) as String
                                        } catch (e: Exception) {
                                            enumValue.name.lowercase()
                                        }
                                    } else {
                                        enumValue.toString()
                                    }
                                    add(enumString)
                                }
                            }
                        }
                        
                        if (!paramAnnotation.required) {
                            // Add default value if available
                            try {
                                javaField.isAccessible = true
                                val defaultValue = javaField.get(command)
                                when (defaultValue) {
                                    is Boolean -> put("default", defaultValue)
                                    is Number -> put("default", defaultValue)
                                    is String -> if (defaultValue.isNotEmpty()) put("default", defaultValue)
                                    is Enum<*> -> {
                                        // For enums, get the string representation
                                        val enumString = try {
                                            val typeMethod = defaultValue.javaClass.getMethod("getType")
                                            typeMethod.invoke(defaultValue) as String
                                        } catch (e: Exception) {
                                            defaultValue.name.lowercase()
                                        }
                                        put("default", enumString)
                                    }
                                    null -> {} // No default
                                    else -> {} // Complex type, skip default
                                }
                            } catch (e: Exception) {
                                // Unable to get default value
                            }
                        }
                    }
                }
            }

            // Check for ParametersDelegate
            val delegateAnnotation = property.javaField?.getAnnotation(ParametersDelegate::class.java)
            if (delegateAnnotation != null && property.javaField != null) {
                try {
                    property.javaField!!.isAccessible = true
                    val delegateObject = property.javaField!!.get(command)
                    if (delegateObject != null) {
                        // Recursively scan delegate object
                        scanDelegateForParameters(delegateObject, properties, requiredFields)
                    }
                } catch (e: Exception) {
                    log.warn { "Unable to process delegate: ${e.message}" }
                }
            }
        }
        
        return buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                properties.forEach { (key, value) ->
                    put(key, value)
                }
            }
            if (requiredFields.isNotEmpty()) {
                putJsonArray("required") {
                    requiredFields.forEach { field ->
                        add(field)
                    }
                }
            }
        }
    }
    
    private fun scanDelegateForParameters(delegate: Any, properties: MutableMap<String, JsonElement>, requiredFields: MutableList<String>) {
        delegate::class.memberProperties.forEach { property ->
            val javaField = property.javaField
            if (javaField != null) {
                val paramAnnotation = javaField.getAnnotation(Parameter::class.java)
                if (paramAnnotation != null) {
                    val fieldName = property.name
                    
                    // Check if delegate field is required
                    if (paramAnnotation.required) {
                        requiredFields.add(fieldName)
                    }
                    
                    properties[fieldName] = buildJsonObject {
                        put("type", determineJsonType(javaField.type))
                        // Use description if available, otherwise use field name as fallback
                        val desc = paramAnnotation.description?.takeIf { it.isNotEmpty() } 
                            ?: "Parameter: $fieldName"
                        put("description", desc)
                    }
                }
            }
        }
    }
    
    private fun determineJsonType(type: Class<*>): String {
        return when {
            type.isEnum -> "string" // Enums are strings with constraints
            type == String::class.java -> "string"
            type == Int::class.java || type == Integer::class.java ||
            type == Long::class.java || type == java.lang.Long::class.java ||
            type == Double::class.java || type == java.lang.Double::class.java ||
            type == Float::class.java || type == java.lang.Float::class.java -> "number"
            type == Boolean::class.java || type == java.lang.Boolean::class.java -> "boolean"
            else -> "string" // Default to string for unknown types
        }
    }
    
    private fun mapArgumentsToCommand(command: ICommand, arguments: JsonObject) {
        log.info { "mapArgumentsToCommand: Mapping arguments to ${command::class.simpleName}: $arguments" }
        command::class.memberProperties.forEach { property ->
            val javaField = property.javaField
            if (javaField != null) {
                val paramAnnotation = javaField.getAnnotation(Parameter::class.java)
                if (paramAnnotation != null) {
                    val fieldName = property.name
                    
                    val value = arguments[fieldName]
                    if (value != null && value !is JsonNull) {
                        log.info { "Setting field '$fieldName' to value: $value" }
                        setFieldValue(command, javaField, value)
                    } else {
                        log.info { "Skipping field '$fieldName' (value is null or JsonNull)" }
                    }
                }
            }
            
            // Handle ParametersDelegate
            val delegateAnnotation = property.javaField?.getAnnotation(ParametersDelegate::class.java)
            if (delegateAnnotation != null && property.javaField != null) {
                try {
                    property.javaField!!.isAccessible = true
                    val delegateObject = property.javaField!!.get(command)
                    if (delegateObject != null) {
                        log.info { "Found delegate object of type ${delegateObject::class.simpleName}, mapping arguments" }
                        mapArgumentsToDelegate(delegateObject, arguments)
                    } else {
                        log.info { "Delegate object is null, skipping" }
                    }
                } catch (e: Exception) {
                    log.warn { "Unable to process delegate: ${e.message}" }
                }
            }
        }
    }
    
    private fun mapArgumentsToDelegate(delegate: Any, arguments: JsonObject) {
        log.info { "mapArgumentsToDelegate: Mapping arguments to delegate ${delegate::class.simpleName}: $arguments" }
        delegate::class.memberProperties.forEach { property ->
            val javaField = property.javaField
            if (javaField != null) {
                val paramAnnotation = javaField.getAnnotation(Parameter::class.java)
                if (paramAnnotation != null) {
                    val fieldName = property.name
                    
                    val value = arguments[fieldName]
                    if (value != null && value !is JsonNull) {
                        log.info { "Setting delegate field '$fieldName' to value: $value" }
                        setFieldValue(delegate, javaField, value)
                    } else {
                        log.info { "Skipping delegate field '$fieldName' (value is null or JsonNull)" }
                    }
                }
            }
        }
    }
    
    private fun setFieldValue(target: Any, field: java.lang.reflect.Field, value: JsonElement) {
        try {
            field.isAccessible = true
            when {
                field.type.isEnum -> {
                    // Handle enum types
                    val enumString = value.jsonPrimitive.content
                    val enumConstants = field.type.enumConstants
                    
                    // Try to find matching enum by 'type' property or name
                    val enumValue = enumConstants.firstOrNull { enumConstant ->
                        if (enumConstant is Enum<*>) {
                            // First try to match by 'type' property if it exists
                            try {
                                val typeMethod = enumConstant.javaClass.getMethod("getType")
                                val typeValue = typeMethod.invoke(enumConstant) as String
                                typeValue == enumString
                            } catch (e: Exception) {
                                // Fall back to name matching
                                enumConstant.name.equals(enumString, ignoreCase = true)
                            }
                        } else {
                            enumConstant.toString() == enumString
                        }
                    }
                    
                    if (enumValue != null) {
                        field.set(target, enumValue)
                    } else {
                        log.warn { "Unable to find enum value '$enumString' for field '${field.name}'" }
                    }
                }
                field.type == String::class.java -> {
                    field.set(target, value.jsonPrimitive.content)
                }
                field.type == Int::class.java || field.type == Integer::class.java -> {
                    field.set(target, value.jsonPrimitive.int)
                }
                field.type == Long::class.java || field.type == java.lang.Long::class.java -> {
                    field.set(target, value.jsonPrimitive.long)
                }
                field.type == Double::class.java || field.type == java.lang.Double::class.java -> {
                    field.set(target, value.jsonPrimitive.double)
                }
                field.type == Float::class.java || field.type == java.lang.Float::class.java -> {
                    field.set(target, value.jsonPrimitive.float)
                }
                field.type == Boolean::class.java || field.type == java.lang.Boolean::class.java -> {
                    field.set(target, value.jsonPrimitive.boolean)
                }
            }
            log.info { "Successfully set field '${field.name}' on ${target::class.simpleName} to value: ${field.get(target)}" }
        } catch (e: Exception) {
            log.warn { "Unable to set field '${field.name}' on ${target::class.simpleName}: ${e.message}" }
        }
    }
}