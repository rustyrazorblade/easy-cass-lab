package com.rustyrazorblade.easycasslab.mcp

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import com.rustyrazorblade.easycasslab.Command
import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.commands.ICommand
import com.rustyrazorblade.easycasslab.di.KoinModules
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.mockito.kotlin.mock

class McpEnumTest {
    private lateinit var context: Context
    private lateinit var registry: McpToolRegistry

    @BeforeEach
    fun setup() {
        context = mock()

        // Initialize Koin for dependency injection
        startKoin { modules(KoinModules.getAllModules(context)) }

        registry = McpToolRegistry(context)
    }

    @AfterEach
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `test enum schema generation with type property`() {
        // Create a test command with enum
        val testCommand = TestCommandWithEnum()
        val command = Command("enum-test", testCommand)

        // Use reflection to call createToolInfo
        val createToolInfoMethod =
            McpToolRegistry::class.java
                .getDeclaredMethod(
                    "createToolInfo",
                    Command::class.java,
                ).apply { isAccessible = true }

        val toolInfo = createToolInfoMethod.invoke(registry, command) as McpToolRegistry.ToolInfo

        // Print the schema to see what's being generated
        println("Generated schema for enum command:")
        println(
            Json { prettyPrint = true }
                .encodeToString(JsonObject.serializer(), toolInfo.inputSchema),
        )

        // Just verify the schema is not null and has some content
        val schema = toolInfo.inputSchema
        assertThat(schema).isNotNull
        assertThat(schema.size).isGreaterThan(0)
    }

    @Test
    fun `test enum value mapping from arguments`() {
        val testCommand = TestCommandWithEnum()
        val command = Command("enum-map-test", testCommand)

        // Create arguments with enum value
        val arguments =
            buildJsonObject {
                put("arch", "arm64")
                put("mode", "production")
            }

        // Map arguments to command
        val mapArgumentsMethod =
            McpToolRegistry::class.java
                .getDeclaredMethod(
                    "mapArgumentsToCommand",
                    ICommand::class.java,
                    JsonObject::class.java,
                ).apply { isAccessible = true }

        mapArgumentsMethod.invoke(registry, testCommand, arguments)

        // Verify enum fields were set correctly
        assertThat(testCommand.arch).isEqualTo(TestArch.ARM64)
        assertThat(testCommand.mode).isEqualTo(TestMode.PRODUCTION)
    }

    // Test enum with type property (like Arch)
    enum class TestArch(
        val type: String,
    ) {
        AMD64("amd64"),
        ARM64("arm64"),
    }

    // Test enum without type property
    enum class TestMode {
        DEVELOPMENT,
        STAGING,
        PRODUCTION,
    }

    @Parameters(commandDescription = "Test command with enum parameters")
    private class TestCommandWithEnum : ICommand {
        @Parameter(names = ["--arch", "-a"], description = "CPU architecture")
        var arch: TestArch = TestArch.AMD64

        @Parameter(names = ["--mode", "-m"], description = "Deployment mode")
        var mode: TestMode = TestMode.DEVELOPMENT

        override fun execute() {
            println("Executing with arch=$arch, mode=$mode")
        }
    }
}
