package com.rustyrazorblade.easycasslab.mcp

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import com.beust.jcommander.ParametersDelegate
import com.rustyrazorblade.easycasslab.Command
import com.rustyrazorblade.easycasslab.CommandLineParser
import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.commands.ICommand
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.*
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaField

/**
 * Registry that manages easy-cass-lab commands as MCP tools.
 */
class McpToolRegistry(private val context: Context) {
    companion object {
        private val log = KotlinLogging.logger {}
        
        // Commands that should not be exposed as MCP tools
        private val EXCLUDED_COMMANDS = setOf(
            "mcp",    // This command itself
            "repl",   // Interactive REPL
            "server"  // HTTP server daemon
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
    fun getTools(): List<ToolInfo> {
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
        val tool = getTools().find { it.name == name }
        
        if (tool == null) {
            return ToolResult(
                content = listOf("Tool not found: $name"),
                isError = true
            )
        }
        
        return try {
            // Map JSON arguments to command parameters
            if (arguments != null) {
                mapArgumentsToCommand(tool.command.command, arguments)
            }
            
            // Capture output
            val output = captureOutput {
                tool.command.command.execute()
            }
            
            ToolResult(
                content = listOf(output)
            )
        } catch (e: Exception) {
            log.error(e) { "Error executing tool: $name" }
            ToolResult(
                content = listOf("Error executing command: ${e.message}"),
                isError = true
            )
        }
    }
    
    private fun createToolInfo(command: Command): ToolInfo {
        val description = extractDescription(command.command)
        val schema = generateSchema(command.command)
        
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
    
    private fun generateSchema(command: ICommand): JsonObject {
        val properties = mutableMapOf<String, JsonElement>()
        
        // Scan all properties for Parameter annotations
        command::class.memberProperties.forEach { property ->
            val javaField = property.javaField
            if (javaField != null) {
                val paramAnnotation = javaField.getAnnotation(Parameter::class.java)
                if (paramAnnotation != null) {
                    val fieldName = paramAnnotation.names.firstOrNull()
                        ?.removePrefix("--")
                        ?.removePrefix("-")
                        ?: property.name
                    
                    properties[fieldName] = buildJsonObject {
                        put("type", determineJsonType(javaField.type))
                        paramAnnotation.description?.let { 
                            put("description", it) 
                        }
                        if (!paramAnnotation.required) {
                            // Add default value if available
                            try {
                                javaField.isAccessible = true
                                val defaultValue = javaField.get(command)
                                when (defaultValue) {
                                    is Boolean -> put("default", defaultValue)
                                    is Number -> put("default", defaultValue)
                                    is String -> put("default", defaultValue)
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
                        scanDelegateForParameters(delegateObject, properties)
                    }
                } catch (e: Exception) {
                    log.warn { "Unable to process delegate: ${e.message}" }
                }
            }
        }
        
        return buildJsonObject {
            putJsonObject("type") {
                put("type", "object")
                putJsonObject("properties") {
                    properties.forEach { (key, value) ->
                        put(key, value)
                    }
                }
            }
        }
    }
    
    private fun scanDelegateForParameters(delegate: Any, properties: MutableMap<String, JsonElement>) {
        delegate::class.memberProperties.forEach { property ->
            val javaField = property.javaField
            if (javaField != null) {
                val paramAnnotation = javaField.getAnnotation(Parameter::class.java)
                if (paramAnnotation != null) {
                    val fieldName = paramAnnotation.names.firstOrNull()
                        ?.removePrefix("--")
                        ?.removePrefix("-")
                        ?: property.name
                    
                    properties[fieldName] = buildJsonObject {
                        put("type", determineJsonType(javaField.type))
                        paramAnnotation.description?.let { 
                            put("description", it) 
                        }
                    }
                }
            }
        }
    }
    
    private fun determineJsonType(type: Class<*>): String {
        return when (type) {
            String::class.java -> "string"
            Int::class.java, Integer::class.java,
            Long::class.java, java.lang.Long::class.java,
            Double::class.java, java.lang.Double::class.java,
            Float::class.java, java.lang.Float::class.java -> "number"
            Boolean::class.java, java.lang.Boolean::class.java -> "boolean"
            else -> "string" // Default to string for unknown types
        }
    }
    
    private fun mapArgumentsToCommand(command: ICommand, arguments: JsonObject) {
        command::class.memberProperties.forEach { property ->
            val javaField = property.javaField
            if (javaField != null) {
                val paramAnnotation = javaField.getAnnotation(Parameter::class.java)
                if (paramAnnotation != null) {
                    val fieldName = paramAnnotation.names.firstOrNull()
                        ?.removePrefix("--")
                        ?.removePrefix("-")
                        ?: property.name
                    
                    val value = arguments[fieldName]
                    if (value != null && value !is JsonNull) {
                        setFieldValue(command, javaField, value)
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
                        mapArgumentsToDelegate(delegateObject, arguments)
                    }
                } catch (e: Exception) {
                    log.warn { "Unable to process delegate: ${e.message}" }
                }
            }
        }
    }
    
    private fun mapArgumentsToDelegate(delegate: Any, arguments: JsonObject) {
        delegate::class.memberProperties.forEach { property ->
            val javaField = property.javaField
            if (javaField != null) {
                val paramAnnotation = javaField.getAnnotation(Parameter::class.java)
                if (paramAnnotation != null) {
                    val fieldName = paramAnnotation.names.firstOrNull()
                        ?.removePrefix("--")
                        ?.removePrefix("-")
                        ?: property.name
                    
                    val value = arguments[fieldName]
                    if (value != null && value !is JsonNull) {
                        setFieldValue(delegate, javaField, value)
                    }
                }
            }
        }
    }
    
    private fun setFieldValue(target: Any, field: java.lang.reflect.Field, value: JsonElement) {
        try {
            field.isAccessible = true
            when (field.type) {
                String::class.java -> {
                    field.set(target, value.jsonPrimitive.content)
                }
                Int::class.java, Integer::class.java -> {
                    field.set(target, value.jsonPrimitive.int)
                }
                Long::class.java, java.lang.Long::class.java -> {
                    field.set(target, value.jsonPrimitive.long)
                }
                Double::class.java, java.lang.Double::class.java -> {
                    field.set(target, value.jsonPrimitive.double)
                }
                Float::class.java, java.lang.Float::class.java -> {
                    field.set(target, value.jsonPrimitive.float)
                }
                Boolean::class.java, java.lang.Boolean::class.java -> {
                    field.set(target, value.jsonPrimitive.boolean)
                }
            }
        } catch (e: Exception) {
            log.warn { "Unable to set field ${field.name}: ${e.message}" }
        }
    }
    
    private fun captureOutput(block: () -> Unit): String {
        val originalOut = System.out
        val originalErr = System.err
        
        val outputStream = ByteArrayOutputStream()
        val printStream = PrintStream(outputStream)
        
        return try {
            System.setOut(printStream)
            System.setErr(printStream)
            
            block()
            
            outputStream.toString()
        } finally {
            System.setOut(originalOut)
            System.setErr(originalErr)
            printStream.close()
        }
    }
}