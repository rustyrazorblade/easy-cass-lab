package com.rustyrazorblade.easydblab.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import com.rustyrazorblade.easydblab.CommandLineParser
import com.rustyrazorblade.easydblab.Context
import com.rustyrazorblade.easydblab.PicoCommandEntry
import com.rustyrazorblade.easydblab.annotations.McpCommand
import com.rustyrazorblade.easydblab.commands.PicoCommand
import io.github.oshai.kotlinlogging.KotlinLogging
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification
import io.modelcontextprotocol.server.McpSyncServerExchange
import io.modelcontextprotocol.spec.McpSchema
import org.koin.core.component.KoinComponent
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

    private val schemaGenerator = McpSchemaGenerator()
    private val jsonMapper = JacksonMcpJsonMapper(ObjectMapper())
    private val toolExecutor = McpToolExecutor()

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
            .callHandler { exchange, request ->
                executeTool(entry, request.arguments(), request.progressToken(), exchange)
            }.build()
    }

    /**
     * Execute a tool on a background thread with progress notifications.
     *
     * Tool execution is delegated to McpToolExecutor which:
     * - Runs commands on a single-thread executor (queue + concurrency limit of 1)
     * - Streams output messages as MCP progress notifications (if progressToken provided)
     * - Handles timeouts and error recovery
     *
     * @param entry The command entry to execute
     * @param arguments The tool arguments from the request
     * @param progressToken The progress token from the client, or null if not tracking progress
     * @param exchange The MCP server exchange
     */
    private fun executeTool(
        entry: PicoCommandEntry,
        arguments: Map<String, Any>?,
        progressToken: Any?,
        exchange: McpSyncServerExchange,
    ): McpSchema.CallToolResult =
        toolExecutor.execute(
            entry = entry,
            arguments = arguments,
            progressToken = progressToken,
            exchange = exchange,
            argumentMapper = { command, args -> mapArgumentsToCommand(command, args) },
        )

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

    /**
     * Shutdown the tool executor.
     *
     * Should be called when the MCP server stops to clean up resources.
     */
    fun shutdown() {
        log.info { "Shutting down MCP tool registry" }
        toolExecutor.shutdown()
    }
}
