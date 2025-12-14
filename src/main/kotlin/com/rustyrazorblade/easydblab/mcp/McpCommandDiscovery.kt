package com.rustyrazorblade.easydblab.mcp

import com.rustyrazorblade.easydblab.Context
import com.rustyrazorblade.easydblab.annotations.McpCommand
import com.rustyrazorblade.easydblab.commands.PicoCommand
import io.github.classgraph.ClassGraph
import io.github.oshai.kotlinlogging.KotlinLogging
import picocli.CommandLine.Command as PicoCommandAnnotation

/**
 * Discovers MCP-enabled commands using ClassGraph classpath scanning.
 *
 * This discovery mechanism:
 * 1. Scans for all classes annotated with @McpCommand
 * 2. Derives tool names from package paths (e.g., cassandra.stress -> cassandra_stress_)
 * 3. Creates factory functions for command instantiation
 *
 * Tool naming convention:
 * - Top-level commands keep their CLI name (e.g., "init", "clean")
 * - Nested commands use package path as prefix (e.g., "cassandra_up", "spark_submit")
 * - Double-nested commands use full path (e.g., "cassandra_stress_start")
 */
object McpCommandDiscovery {
    private val log = KotlinLogging.logger {}
    private const val COMMANDS_PACKAGE = "com.rustyrazorblade.easydblab.commands"

    /**
     * Discovers all commands annotated with @McpCommand.
     *
     * @param context The Context to pass to command constructors
     * @return List of McpCommandEntry for each discovered command
     */
    @Suppress("TooGenericExceptionCaught")
    fun discoverMcpCommands(context: Context): List<McpCommandEntry> {
        log.info { "Scanning for @McpCommand annotated classes in $COMMANDS_PACKAGE" }

        return ClassGraph()
            .enableClassInfo()
            .enableAnnotationInfo()
            .acceptPackages(COMMANDS_PACKAGE)
            .scan()
            .use { scanResult ->
                val mcpClasses =
                    scanResult
                        .getClassesWithAnnotation(McpCommand::class.java)
                        .filter { classInfo ->
                            // Only include concrete classes that implement PicoCommand
                            !classInfo.isAbstract &&
                                !classInfo.isInterface &&
                                classInfo.implementsInterface(PicoCommand::class.java)
                        }

                log.info { "Found ${mcpClasses.size} @McpCommand annotated classes" }

                mcpClasses.mapNotNull { classInfo ->
                    try {
                        @Suppress("UNCHECKED_CAST")
                        val commandClass = classInfo.loadClass() as Class<out PicoCommand>
                        val toolName = deriveMcpToolName(commandClass)

                        log.debug { "Discovered MCP command: $toolName from ${commandClass.simpleName}" }

                        McpCommandEntry(
                            toolName = toolName,
                            factory = { createCommandInstance(commandClass, context) },
                            commandClass = commandClass,
                        )
                    } catch (e: Exception) {
                        log.warn { "Failed to load command class ${classInfo.name}: ${e.message}" }
                        null
                    }
                }
            }
    }

    /**
     * Derives the MCP tool name from a command class's package and @Command annotation.
     *
     * Examples:
     * - com.rustyrazorblade.easydblab.commands.Init (name="init") -> "init"
     * - com.rustyrazorblade.easydblab.commands.cassandra.Up (name="up") -> "cassandra_up"
     * - com.rustyrazorblade.easydblab.commands.cassandra.stress.StressStart (name="start") -> "cassandra_stress_start"
     *
     * @param commandClass The command class to derive the name from
     * @return The MCP tool name
     */
    fun deriveMcpToolName(commandClass: Class<*>): String {
        val packageName = commandClass.`package`.name

        // Get the relative package path from base commands package
        val relativePath = packageName.removePrefix("$COMMANDS_PACKAGE.")

        // Get command name from @Command annotation
        val commandAnnotation = commandClass.getAnnotation(PicoCommandAnnotation::class.java)
        val commandName =
            commandAnnotation?.name
                ?: commandClass.simpleName.lowercase()

        // If no sub-package (relativePath equals original package), return just the command name
        if (relativePath == packageName || relativePath.isEmpty()) {
            return commandName
        }

        // Build prefixed name: package_segments_commandname
        val prefix = relativePath.replace(".", "_")
        return "${prefix}_$commandName"
    }

    /**
     * Creates an instance of a command class using reflection.
     *
     * Commands are expected to have a constructor that takes Context as the only parameter.
     *
     * @param commandClass The class to instantiate
     * @param context The Context to pass to the constructor
     * @return A new instance of the command
     */
    @Suppress("TooGenericExceptionCaught")
    private fun createCommandInstance(
        commandClass: Class<out PicoCommand>,
        context: Context,
    ): PicoCommand {
        try {
            // Find constructor that takes Context parameter
            val constructor = commandClass.getConstructor(Context::class.java)
            return constructor.newInstance(context)
        } catch (e: NoSuchMethodException) {
            // Try no-arg constructor as fallback
            try {
                return commandClass.getDeclaredConstructor().newInstance()
            } catch (e2: NoSuchMethodException) {
                throw IllegalArgumentException(
                    "Command ${commandClass.simpleName} must have a constructor that takes Context or a no-arg constructor",
                    e2,
                )
            }
        }
    }
}

/**
 * Represents an MCP command entry discovered through classpath scanning.
 *
 * @property toolName The MCP tool name (e.g., "cassandra_stress_start")
 * @property factory Factory function to create a new command instance
 * @property commandClass The command class for reflection and schema generation
 */
data class McpCommandEntry(
    val toolName: String,
    val factory: () -> PicoCommand,
    val commandClass: Class<out PicoCommand>,
)
