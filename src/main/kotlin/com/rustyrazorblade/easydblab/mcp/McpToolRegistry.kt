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
            }.map { entry -> createToolSpecification(entry) }
    }

    private fun createToolSpecification(entry: PicoCommandEntry): SyncToolSpecification {
        val tempCommand = entry.factory()
        val description = extractDescription(tempCommand)
        val schemaJson = schemaGenerator.generateSchema(tempCommand).toJson()

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

        return SyncToolSpecification
            .builder()
            .tool(tool)
            .callHandler { _, request ->
                executeTool(entry, request.arguments())
            }.build()
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

            McpSchema.CallToolResult
                .builder()
                .addTextContent("Tool '${entry.name}' executed successfully")
                .isError(false)
                .build()
        } catch (e: Exception) {
            log.error(e) { "Error executing tool ${entry.name}" }
            outputHandler.handleError("Tool '${entry.name}' failed: ${e.message}", e)

            McpSchema.CallToolResult
                .builder()
                .addTextContent("Error: ${e.message}")
                .isError(true)
                .build()
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
        applyArguments(command, arguments)
    }

    /**
     * Recursively apply arguments to an object and its @Mixin fields.
     */
    @Suppress("TooGenericExceptionCaught")
    private fun applyArguments(
        target: Any,
        arguments: Map<String, Any>,
    ) {
        target::class.memberProperties.forEach { property ->
            val javaField = property.javaField ?: return@forEach

            // Process @Option annotations
            javaField.getAnnotation(Option::class.java)?.let {
                arguments[property.name]?.let { value ->
                    setFieldValue(target, javaField, value)
                }
            }

            // Process @Mixin annotations (recursively apply to nested objects)
            javaField.getAnnotation(Mixin::class.java)?.let {
                ReflectionUtils.getMixinObject(javaField, target)?.let { mixinObj ->
                    applyArguments(mixinObj, arguments)
                }
            }
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
                TypeChecker.isInt(field.type) -> field.set(target, TypeConverter.toInt(value))
                TypeChecker.isLong(field.type) -> field.set(target, TypeConverter.toLong(value))
                TypeChecker.isDouble(field.type) -> field.set(target, TypeConverter.toDouble(value))
                TypeChecker.isFloat(field.type) -> field.set(target, TypeConverter.toFloat(value))
                TypeChecker.isBoolean(field.type) -> field.set(target, TypeConverter.toBoolean(value))
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
}
