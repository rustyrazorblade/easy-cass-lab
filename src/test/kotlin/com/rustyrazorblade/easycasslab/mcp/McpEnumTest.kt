package com.rustyrazorblade.easycasslab.mcp

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import com.rustyrazorblade.easycasslab.Command
import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.commands.ICommand
import kotlinx.serialization.json.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class McpEnumTest {
    private lateinit var context: Context
    private lateinit var registry: McpToolRegistry

    @BeforeEach
    fun setup() {
        context = mock()
        registry = McpToolRegistry(context)
    }

    @Test
    fun `test enum schema generation with type property`() {
        // Create a test command with enum
        val testCommand = TestCommandWithEnum()
        val command = Command("enum-test", testCommand)
        
        // Use reflection to call createToolInfo
        val createToolInfoMethod = McpToolRegistry::class.java.getDeclaredMethod(
            "createToolInfo",
            Command::class.java
        ).apply { isAccessible = true }
        
        val toolInfo = createToolInfoMethod.invoke(registry, command) as McpToolRegistry.ToolInfo
        
        // Print the schema to see what's being generated
        println("Generated schema for enum command:")
        println(Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), toolInfo.inputSchema))
        
        // Verify the schema structure
        val schema = toolInfo.inputSchema
        val properties = schema["properties"]?.jsonObject
        assertThat(properties).isNotNull
        
        // Check the arch field has enum constraint
        val archField = properties!!["arch"]?.jsonObject
        assertThat(archField).isNotNull
        assertThat(archField!!["type"]?.jsonPrimitive?.content).isEqualTo("string")
        
        // Verify enum values are present
        val enumArray = archField["enum"]?.jsonArray
        assertThat(enumArray).isNotNull
        assertThat(enumArray!!.size).isEqualTo(2)
        
        // Check that enum values are correct
        val enumValues = enumArray.map { it.jsonPrimitive.content }
        assertThat(enumValues).containsExactlyInAnyOrder("amd64", "arm64")
        
        // Check default value
        val defaultValue = archField["default"]?.jsonPrimitive?.content
        assertThat(defaultValue).isEqualTo("amd64")
    }
    
    @Test
    fun `test enum value mapping from arguments`() {
        val testCommand = TestCommandWithEnum()
        val command = Command("enum-map-test", testCommand)
        
        // Create arguments with enum value
        val arguments = buildJsonObject {
            put("arch", "arm64")
            put("mode", "production")
        }
        
        // Map arguments to command
        val mapArgumentsMethod = McpToolRegistry::class.java.getDeclaredMethod(
            "mapArgumentsToCommand",
            ICommand::class.java,
            JsonObject::class.java
        ).apply { isAccessible = true }
        
        mapArgumentsMethod.invoke(registry, testCommand, arguments)
        
        // Verify enum fields were set correctly
        assertThat(testCommand.arch).isEqualTo(TestArch.arm64)
        assertThat(testCommand.mode).isEqualTo(TestMode.PRODUCTION)
    }
    
    // Test enum with type property (like Arch)
    enum class TestArch(val type: String) {
        amd64("amd64"),
        arm64("arm64")
    }
    
    // Test enum without type property
    enum class TestMode {
        DEVELOPMENT,
        STAGING,
        PRODUCTION
    }
    
    @Parameters(commandDescription = "Test command with enum parameters")
    private class TestCommandWithEnum : ICommand {
        @Parameter(names = ["--arch", "-a"], description = "CPU architecture")
        var arch: TestArch = TestArch.amd64
        
        @Parameter(names = ["--mode", "-m"], description = "Deployment mode")
        var mode: TestMode = TestMode.DEVELOPMENT
        
        override fun execute() {
            println("Executing with arch=$arch, mode=$mode")
        }
    }
}