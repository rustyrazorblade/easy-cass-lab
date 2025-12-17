package com.rustyrazorblade.easydblab.mcp

import com.rustyrazorblade.easydblab.BaseKoinTest
import com.rustyrazorblade.easydblab.Context
import com.rustyrazorblade.easydblab.PicoCommandEntry
import com.rustyrazorblade.easydblab.commands.PicoBaseCommand
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import picocli.CommandLine.Command
import picocli.CommandLine.Option

class McpServerSimpleTest : BaseKoinTest() {
    private lateinit var registry: McpToolRegistry

    @BeforeEach
    fun setup() {
        registry = McpToolRegistry(context)
    }

    @Test
    fun `test schema generation for simple command`() {
        // Create a test command entry
        val entry = PicoCommandEntry("simple-test", { SimpleTestCommand(context) })

        // Use reflection to call createToolInfoFromPico
        val createToolInfoMethod =
            McpToolRegistry::class.java
                .getDeclaredMethod(
                    "createToolInfoFromPico",
                    PicoCommandEntry::class.java,
                ).apply { isAccessible = true }

        val toolInfo = createToolInfoMethod.invoke(registry, entry) as McpToolRegistry.ToolInfo

        // Print the schema to see what's being generated
        println("Generated schema for simple command:")
        println(Json.encodeToString(JsonObject.serializer(), toolInfo.inputSchema))

        // Just verify the schema is not null and has some content
        val schema = toolInfo.inputSchema
        assertThat(schema).isNotNull
        assertThat(schema.size).isGreaterThan(0)

        // Verify the tool has the basic properties we expect
        // Note: hyphens are normalized to underscores in tool names
        assertThat(toolInfo.name).isEqualTo("simple_test")
        assertThat(toolInfo.description).isEqualTo("Simple test command")
    }

    @Command(name = "simple-test", description = ["Simple test command"])
    class SimpleTestCommand(
        context: Context,
    ) : PicoBaseCommand(context) {
        @Option(names = ["--name", "-n"], description = ["Name parameter"], required = true)
        var name: String = ""

        @Option(names = ["--count"], description = ["Count parameter"])
        var count: Int = 0

        @Option(names = ["--enabled"], description = ["Enabled flag"])
        var enabled: Boolean = false

        override fun execute() {
            println("Executing with name=$name, count=$count, enabled=$enabled")
        }
    }
}
