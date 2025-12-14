package com.rustyrazorblade.easydblab.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import com.rustyrazorblade.easydblab.CommandLineParser
import com.rustyrazorblade.easydblab.Context
import com.rustyrazorblade.easydblab.PicoCommandEntry
import com.rustyrazorblade.easydblab.annotations.McpCommand
import com.rustyrazorblade.easydblab.commands.PicoCommand
import com.rustyrazorblade.easydblab.output.OutputHandler
import io.github.oshai.kotlinlogging.KotlinLogging
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification
import io.modelcontextprotocol.spec.McpSchema
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import picocli.CommandLine.Mixin
import picocli.CommandLine.Option
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaField
import picocli.CommandLine.Command as PicoCommandAnnotation

/**
 * Registry that discovers and registers PicoCLI commands as MCP tools.
 *
 * This class:
 * 1. Scans for commands annotated with @McpCommand
 * 2. Generates JSON schemas from @Option annotations
 * 3. Creates SyncToolSpecifications for the Java MCP SDK
 * 4. Executes tools synchronously (blocking until completion)
 */
class McpToolRegistry(
    private val context: Context,
) : KoinComponent {
    companion object {
        private val log = KotlinLogging.logger {}
    }

    private val outputHandler: OutputHandler by inject()
    private val schemaGenerator = McpSchemaGenerator()
    private val jsonMapper = JacksonMcpJsonMapper(ObjectMapper())

    /**
     * Get all available tools as SyncToolSpecification for the Java MCP SDK.
     */
    fun getToolSpecifications(): List<SyncToolSpecification> {
        val parser = CommandLineParser(context)

        return parser.picoCommands
            .filter { entry ->
                val tempCommand = entry.factory()
                tempCommand::class.java.isAnnotationPresent(McpCommand::class.java)
            }
            .map { entry -> createToolSpecification(entry) }
    }

    private fun createToolSpecification(entry: PicoCommandEntry): SyncToolSpecification {
        val tempCommand = entry.factory()
        val description = extractDescription(tempCommand)
        val schemaJson = schemaGenerator.generateSchema(tempCommand)

        log.info { "Creating tool specification for ${entry.name}: $description" }
        log.debug { "Schema for ${entry.name}: $schemaJson" }

        // Use builder pattern for Tool
        val tool =
            McpSchema.Tool
                .builder()
                .name(entry.name)
                .description(description)
                .inputSchema(jsonMapper, schemaJson)
                .build()

        return SyncToolSpecification(tool) { _, arguments ->
            executeTool(entry, arguments)
        }
    }

    /**
     * Execute a tool synchronously.
     *
     * Unlike the Kotlin SDK implementation, this blocks until the command
     * completes and returns the result directly.
     */
    @Suppress("TooGenericExceptionCaught")
    private fun executeTool(
        entry: PicoCommandEntry,
        arguments: Map<String, Any>?,
    ): McpSchema.CallToolResult {
        log.info { "Executing tool: ${entry.name} with arguments: $arguments" }

        return try {
            // Create fresh command instance
            val command = entry.factory()

            // Map arguments to command options
            arguments?.let { args ->
                mapArgumentsToCommand(command, args)
            }

            // Execute synchronously
            outputHandler.handleMessage("Starting execution of tool: ${entry.name}")
            command.call()
            outputHandler.handleMessage("Tool '${entry.name}' completed successfully")

            McpSchema.CallToolResult("Tool '${entry.name}' executed successfully", false)
        } catch (e: Exception) {
            log.error(e) { "Error executing tool ${entry.name}" }
            outputHandler.handleError("Tool '${entry.name}' failed: ${e.message}", e)

            McpSchema.CallToolResult("Error: ${e.message}", true)
        }
    }

    private fun extractDescription(command: PicoCommand): String {
        val annotation = command::class.java.getAnnotation(PicoCommandAnnotation::class.java)
        return annotation?.description?.firstOrNull() ?: "No description available"
    }

    private fun mapArgumentsToCommand(
        command: PicoCommand,
        arguments: Map<String, Any>,
    ) {
        log.debug { "Mapping arguments to PicoCLI command ${command::class.simpleName}" }

        command::class.memberProperties.forEach { property ->
            val javaField = property.javaField ?: return@forEach

            // Process @Option annotations
            javaField.getAnnotation(Option::class.java)?.let {
                val value = arguments[property.name]
                if (value != null) {
                    setFieldValue(command, javaField, value)
                }
            }

            // Process @Mixin annotations
            javaField.getAnnotation(Mixin::class.java)?.let {
                processMixinArguments(command, javaField, arguments)
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun processMixinArguments(
        command: PicoCommand,
        mixinField: java.lang.reflect.Field,
        arguments: Map<String, Any>,
    ) {
        try {
            mixinField.isAccessible = true
            val mixinObject = mixinField.get(command) ?: return

            mixinObject::class.memberProperties.forEach { property ->
                val javaField = property.javaField ?: return@forEach

                javaField.getAnnotation(Option::class.java)?.let {
                    val value = arguments[property.name]
                    if (value != null) {
                        setFieldValue(mixinObject, javaField, value)
                    }
                }
            }
        } catch (e: Exception) {
            log.warn { "Unable to process mixin arguments: ${e.message}" }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun setFieldValue(
        target: Any,
        field: java.lang.reflect.Field,
        value: Any,
    ) {
        try {
            field.isAccessible = true
            when {
                field.type.isEnum -> setEnumFieldValue(field, target, value)
                field.type == String::class.java -> field.set(target, value.toString())
                isIntType(field.type) -> field.set(target, convertToInt(value))
                isLongType(field.type) -> field.set(target, convertToLong(value))
                isDoubleType(field.type) -> field.set(target, convertToDouble(value))
                isFloatType(field.type) -> field.set(target, convertToFloat(value))
                isBooleanType(field.type) -> field.set(target, convertToBoolean(value))
            }
            log.debug { "Set field '${field.name}' on ${target::class.simpleName} to $value" }
        } catch (e: Exception) {
            log.warn { "Unable to set field '${field.name}' on ${target::class.simpleName}: ${e.message}" }
        }
    }

    private fun setEnumFieldValue(
        field: java.lang.reflect.Field,
        target: Any,
        value: Any,
    ) {
        val enumString = value.toString()
        val enumValue = findMatchingEnumValue(field.type, enumString)

        enumValue?.let {
            field.set(target, it)
        } ?: log.warn { "Unable to find enum value '$enumString' for field '${field.name}'" }
    }

    @Suppress("SwallowedException")
    private fun findMatchingEnumValue(
        enumType: Class<*>,
        enumString: String,
    ): Any? =
        enumType.enumConstants.firstOrNull { enumConstant ->
            if (enumConstant is Enum<*>) {
                // Try getType() method first
                try {
                    val typeMethod = enumConstant.javaClass.getMethod("getType")
                    val typeValue = typeMethod.invoke(enumConstant) as String
                    typeValue == enumString
                } catch (e: Exception) {
                    enumConstant.name.equals(enumString, ignoreCase = true)
                }
            } else {
                enumConstant.toString() == enumString
            }
        }

    private fun convertToInt(value: Any): Int =
        when (value) {
            is Number -> value.toInt()
            else -> value.toString().toInt()
        }

    private fun convertToLong(value: Any): Long =
        when (value) {
            is Number -> value.toLong()
            else -> value.toString().toLong()
        }

    private fun convertToDouble(value: Any): Double =
        when (value) {
            is Number -> value.toDouble()
            else -> value.toString().toDouble()
        }

    private fun convertToFloat(value: Any): Float =
        when (value) {
            is Number -> value.toFloat()
            else -> value.toString().toFloat()
        }

    private fun convertToBoolean(value: Any): Boolean =
        when (value) {
            is Boolean -> value
            else -> value.toString().toBoolean()
        }

    private fun isIntType(type: Class<*>): Boolean = type == Int::class.java || type == Integer::class.java

    private fun isLongType(type: Class<*>): Boolean = type == Long::class.java || type == java.lang.Long::class.java

    private fun isDoubleType(type: Class<*>): Boolean = type == Double::class.java || type == java.lang.Double::class.java

    private fun isFloatType(type: Class<*>): Boolean = type == Float::class.java || type == java.lang.Float::class.java

    private fun isBooleanType(type: Class<*>): Boolean = type == Boolean::class.java || type == java.lang.Boolean::class.java
}
