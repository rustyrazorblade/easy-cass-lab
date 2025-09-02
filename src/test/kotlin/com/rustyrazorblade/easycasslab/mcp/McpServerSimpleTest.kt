package com.rustyrazorblade.easycasslab.mcp

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import com.rustyrazorblade.easycasslab.Command
import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.commands.ICommand
import com.rustyrazorblade.easycasslab.di.outputModule
import kotlinx.serialization.json.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.mockito.kotlin.mock

class McpServerSimpleTest {
    private lateinit var context: Context
    private lateinit var registry: McpToolRegistry

    @BeforeEach
    fun setup() {
        // Initialize Koin for dependency injection
        startKoin {
            modules(listOf(outputModule))
        }
        
        context = mock()
        registry = McpToolRegistry(context)
    }
    
    @AfterEach
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `test schema generation for simple command`() {
        // Create a test command
        val testCommand = SimpleTestCommand()
        val command = Command("simple-test", testCommand)
        
        // Use reflection to call createToolInfo
        val createToolInfoMethod = McpToolRegistry::class.java.getDeclaredMethod(
            "createToolInfo",
            Command::class.java
        ).apply { isAccessible = true }
        
        val toolInfo = createToolInfoMethod.invoke(registry, command) as McpToolRegistry.ToolInfo
        
        // Print the schema to see what's being generated
        println("Generated schema for simple command:")
        println(Json.encodeToString(JsonObject.serializer(), toolInfo.inputSchema))
        
        // Just verify the schema is not null and has some content
        val schema = toolInfo.inputSchema
        assertThat(schema).isNotNull
        assertThat(schema.size).isGreaterThan(0)
        
        // Verify the tool has the basic properties we expect
        assertThat(toolInfo.name).isEqualTo("simple-test")
        assertThat(toolInfo.description).isEqualTo("Simple test command")
    }
    
    @Parameters(commandDescription = "Simple test command")
    private class SimpleTestCommand : ICommand {
        @Parameter(names = ["--name", "-n"], description = "Name parameter", required = true)
        var name: String = ""
        
        @Parameter(names = ["--count"], description = "Count parameter")
        var count: Int = 0
        
        @Parameter(names = ["--enabled"], description = "Enabled flag")
        var enabled: Boolean = false
        
        override fun execute() {
            println("Executing with name=$name, count=$count, enabled=$enabled")
        }
    }
}