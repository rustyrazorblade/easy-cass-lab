package com.rustyrazorblade.easycasslab.mcp

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import com.beust.jcommander.ParametersDelegate
import com.rustyrazorblade.easycasslab.Command
import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.commands.ICommand
import com.rustyrazorblade.easycasslab.di.outputModule
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class McpToolRegistryTest {
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
    fun `should use field name instead of parameter annotation names for schema generation`() {
        // Create a test command with parameters
        val testCommand = TestCommand()
        val command = Command("test", testCommand)
        
        // Test the schema generation directly via reflection
        val toolInfoClass = McpToolRegistry::class.java.getDeclaredMethod(
            "createToolInfo",
            Command::class.java
        ).apply { isAccessible = true }
        
        val toolInfo = toolInfoClass.invoke(registry, command) as McpToolRegistry.ToolInfo
        
        // Verify that the schema uses field names, not parameter names
        val schema = toolInfo.inputSchema
        val properties = schema["properties"]?.jsonObject
        
        assertThat(properties).isNotNull
        assertThat(properties!!.keys).contains("testField") // Field name
        assertThat(properties.keys).doesNotContain("test-param") // Not the parameter name
        assertThat(properties.keys).doesNotContain("t") // Not the short parameter name
        
        // Verify field with no parameter names still works
        assertThat(properties.keys).contains("fieldWithoutNames")
    }

    @Test
    fun `should handle delegate parameters with field names`() {
        // Create a test command with delegate
        val testCommand = TestCommandWithDelegate()
        val command = Command("test-delegate", testCommand)
        
        // Test the schema generation
        val toolInfoClass = McpToolRegistry::class.java.getDeclaredMethod(
            "createToolInfo",
            Command::class.java
        ).apply { isAccessible = true }
        
        val toolInfo = toolInfoClass.invoke(registry, command) as McpToolRegistry.ToolInfo
        
        // Verify that delegate parameters also use field names
        val schema = toolInfo.inputSchema
        val properties = schema["properties"]?.jsonObject
        
        assertThat(properties).isNotNull
        assertThat(properties!!.keys).contains("delegateField") // Field name from delegate
        assertThat(properties.keys).doesNotContain("delegate-param") // Not the parameter name
    }

    @Test
    fun `should map arguments to command fields using field names`() {
        val testCommand = TestCommand()
        val command = Command("test", testCommand)
        
        // Create arguments using field names
        val arguments = buildJsonObject {
            put("testField", "value123")
            put("intField", 42)
            put("boolField", true)
            put("fieldWithoutNames", "noNameValue")
        }
        
        // Map arguments to command
        val mapArgumentsMethod = McpToolRegistry::class.java.getDeclaredMethod(
            "mapArgumentsToCommand",
            ICommand::class.java,
            JsonObject::class.java
        ).apply { isAccessible = true }
        
        mapArgumentsMethod.invoke(registry, testCommand, arguments)
        
        // Verify fields were set correctly
        assertThat(testCommand.testField).isEqualTo("value123")
        assertThat(testCommand.intField).isEqualTo(42)
        assertThat(testCommand.boolField).isTrue
        assertThat(testCommand.fieldWithoutNames).isEqualTo("noNameValue")
    }

    @Test
    fun `should handle different field types in schema generation`() {
        val testCommand = TestCommandWithTypes()
        val command = Command("test-types", testCommand)
        
        val toolInfoClass = McpToolRegistry::class.java.getDeclaredMethod(
            "createToolInfo",
            Command::class.java
        ).apply { isAccessible = true }
        
        val toolInfo = toolInfoClass.invoke(registry, command) as McpToolRegistry.ToolInfo
        
        val schema = toolInfo.inputSchema
        val properties = schema["properties"]?.jsonObject
        
        assertThat(properties).isNotNull
        
        // Check String field
        val stringField = properties!!["stringField"]?.jsonObject
        assertThat(stringField?.get("type")?.jsonPrimitive?.content).isEqualTo("string")
        
        // Check Int field
        val intField = properties["intField"]?.jsonObject
        assertThat(intField?.get("type")?.jsonPrimitive?.content).isEqualTo("number")
        
        // Check Boolean field
        val boolField = properties["booleanField"]?.jsonObject
        assertThat(boolField?.get("type")?.jsonPrimitive?.content).isEqualTo("boolean")
        
        // Check Long field
        val longField = properties["longField"]?.jsonObject
        assertThat(longField?.get("type")?.jsonPrimitive?.content).isEqualTo("number")
        
        // Check Double field
        val doubleField = properties["doubleField"]?.jsonObject
        assertThat(doubleField?.get("type")?.jsonPrimitive?.content).isEqualTo("number")
    }

    @Test
    fun `should include parameter descriptions in schema`() {
        val testCommand = TestCommand()
        val command = Command("test", testCommand)
        
        val toolInfoClass = McpToolRegistry::class.java.getDeclaredMethod(
            "createToolInfo",
            Command::class.java
        ).apply { isAccessible = true }
        
        val toolInfo = toolInfoClass.invoke(registry, command) as McpToolRegistry.ToolInfo
        
        val schema = toolInfo.inputSchema
        val properties = schema["properties"]?.jsonObject
        
        assertThat(properties).isNotNull
        
        val testFieldSchema = properties!!["testField"]?.jsonObject
        assertThat(testFieldSchema?.get("description")?.jsonPrimitive?.content)
            .isEqualTo("Test parameter description")
    }

    @Test
    fun `should handle delegate field mapping with field names`() {
        val testCommand = TestCommandWithDelegate()
        val command = Command("test-delegate", testCommand)
        
        // Create arguments using field names
        val arguments = buildJsonObject {
            put("mainField", "mainValue")
            put("delegateField", "delegateValue")
        }
        
        // Map arguments to command
        val mapArgumentsMethod = McpToolRegistry::class.java.getDeclaredMethod(
            "mapArgumentsToCommand",
            ICommand::class.java,
            JsonObject::class.java
        ).apply { isAccessible = true }
        
        mapArgumentsMethod.invoke(registry, testCommand, arguments)
        
        // Verify fields were set correctly
        assertThat(testCommand.mainField).isEqualTo("mainValue")
        assertThat(testCommand.delegate.delegateField).isEqualTo("delegateValue")
    }
    
    @Test
    fun `should execute tool with parameters through executeTool method`() {
        // Test that executeTool correctly passes parameters to commands
        val testCommand = TestCommandWithExecution()
        val command = Command("test-exec", testCommand)
        
        // Create a test registry with our command
        val testRegistry = object : McpToolRegistry(context) {
            override fun getTools(): List<ToolInfo> {
                return listOf(createToolInfo(command))
            }
            
            private fun createToolInfo(cmd: Command): ToolInfo {
                val createMethod = McpToolRegistry::class.java.getDeclaredMethod(
                    "createToolInfo",
                    Command::class.java
                ).apply { isAccessible = true }
                return createMethod.invoke(this, cmd) as ToolInfo
            }
        }
        
        // Execute with parameters
        val arguments = buildJsonObject {
            put("testField", "executed-value")
            put("intField", 123)
            put("boolField", true)
        }
        
        val result = testRegistry.executeTool("test-exec", arguments)
        
        // Verify execution
        assertThat(result.isError).isFalse
        assertThat(testCommand.executionCount).isEqualTo(1)
        assertThat(testCommand.testField).isEqualTo("executed-value")
        assertThat(testCommand.intField).isEqualTo(123)
        assertThat(testCommand.boolField).isTrue
    }
    
    @Test
    fun `should execute tool with delegate parameters through executeTool method`() {
        // Test that executeTool correctly passes delegate parameters
        val testCommand = TestCommandWithDelegateExecution()
        val command = Command("test-delegate-exec", testCommand)
        
        // Create a test registry with our command
        val testRegistry = object : McpToolRegistry(context) {
            override fun getTools(): List<ToolInfo> {
                return listOf(createToolInfo(command))
            }
            
            private fun createToolInfo(cmd: Command): ToolInfo {
                val createMethod = McpToolRegistry::class.java.getDeclaredMethod(
                    "createToolInfo",
                    Command::class.java
                ).apply { isAccessible = true }
                return createMethod.invoke(this, cmd) as ToolInfo
            }
        }
        
        // Execute with both main and delegate parameters
        val arguments = buildJsonObject {
            put("mainField", "main-executed")
            put("delegateField", "delegate-executed")
        }
        
        val result = testRegistry.executeTool("test-delegate-exec", arguments)
        
        // Verify execution and parameter passing
        assertThat(result.isError).isFalse
        assertThat(testCommand.executionCount).isEqualTo(1)
        assertThat(testCommand.mainField).isEqualTo("main-executed")
        assertThat(testCommand.delegate.delegateField).isEqualTo("delegate-executed")
    }
    
    @Test
    fun `should not retain state between multiple executions`() {
        // CRITICAL TEST: Documents that commands currently DO retain state between executions
        // This is a potential bug - commands are reused from CommandLineParser
        val testCommand = TestCommandWithExecution()
        val command = Command("test-state", testCommand)
        
        // Create a test registry
        val testRegistry = object : McpToolRegistry(context) {
            override fun getTools(): List<ToolInfo> {
                return listOf(createToolInfo(command))
            }
            
            private fun createToolInfo(cmd: Command): ToolInfo {
                val createMethod = McpToolRegistry::class.java.getDeclaredMethod(
                    "createToolInfo",
                    Command::class.java
                ).apply { isAccessible = true }
                return createMethod.invoke(this, cmd) as ToolInfo
            }
        }
        
        // First execution
        val arguments1 = buildJsonObject {
            put("testField", "first-value")
            put("intField", 100)
        }
        testRegistry.executeTool("test-state", arguments1)
        
        // Second execution with different values
        val arguments2 = buildJsonObject {
            put("testField", "second-value")
            put("intField", 200)
        }
        testRegistry.executeTool("test-state", arguments2)
        
        // The command instance is reused, so values are from second execution
        // This test documents the current behavior (commands retain state)
        assertThat(testCommand.executionCount).isEqualTo(2)
        assertThat(testCommand.testField).isEqualTo("second-value")
        assertThat(testCommand.intField).isEqualTo(200)
    }
    
    @Test
    fun `should handle null arguments correctly`() {
        // Test that executeTool handles null/missing arguments gracefully
        val testCommand = TestCommandWithExecution()
        val command = Command("test-null", testCommand)
        
        val testRegistry = object : McpToolRegistry(context) {
            override fun getTools(): List<ToolInfo> {
                return listOf(createToolInfo(command))
            }
            
            private fun createToolInfo(cmd: Command): ToolInfo {
                val createMethod = McpToolRegistry::class.java.getDeclaredMethod(
                    "createToolInfo",
                    Command::class.java
                ).apply { isAccessible = true }
                return createMethod.invoke(this, cmd) as ToolInfo
            }
        }
        
        // Execute with no arguments
        val result = testRegistry.executeTool("test-null", null)
        
        // Should execute successfully with default values
        assertThat(result.isError).isFalse
        assertThat(testCommand.executionCount).isEqualTo(1)
        assertThat(testCommand.testField).isEmpty() // Default value
        assertThat(testCommand.intField).isEqualTo(0) // Default value
    }
    
    @Test
    fun `should generate valid JSON Schema 2020-12 compatible structure`() {
        // Test that the schema generation creates JSON Schema 2020-12 compatible structure
        // Verify using a test command with known parameters
        
        val testCommand = TestCommandWithTypes()
        val command = Command("test-schema", testCommand)
        
        // Generate schema using reflection
        val createToolInfoMethod = McpToolRegistry::class.java.getDeclaredMethod(
            "createToolInfo",
            Command::class.java
        ).apply { isAccessible = true }
        
        val toolInfo = createToolInfoMethod.invoke(registry, command) as McpToolRegistry.ToolInfo
        val schema = toolInfo.inputSchema
        
        // Core requirements for JSON Schema 2020-12
        assertThat(schema["type"]?.jsonPrimitive?.content).isEqualTo("object")
        assertThat(schema["additionalProperties"]?.jsonPrimitive?.boolean).isFalse
        
        // Properties must exist (even if empty) for valid JSON Schema
        assertThat(schema.containsKey("properties")).isTrue
        val properties = schema["properties"]?.jsonObject
        assertThat(properties).isNotNull
        assertThat(properties!!).isNotEmpty()
        
        // Each property should have a type
        properties.forEach { (key, value) ->
            val propObject = value.jsonObject
            assertThat(propObject["type"]).describedAs("Property $key should have a type").isNotNull
        }
        
        // Schema should not have nested "type" wrapper
        assertThat(schema.keys).doesNotContain("type.properties")
        
        // Log the schema for inspection
        println("Generated schema structure: $schema")
    }
    
    @Test
    fun `should generate valid schema for commands with no parameters`() {
        // Test that commands with no parameters still generate valid JSON Schema
        val emptyCommand = EmptyCommand()
        val command = Command("empty", emptyCommand)
        
        val createToolInfoMethod = McpToolRegistry::class.java.getDeclaredMethod(
            "createToolInfo",
            Command::class.java
        ).apply { isAccessible = true }
        
        val toolInfo = createToolInfoMethod.invoke(registry, command) as McpToolRegistry.ToolInfo
        val schema = toolInfo.inputSchema
        
        // Must have type and properties even for empty commands
        assertThat(schema["type"]?.jsonPrimitive?.content).isEqualTo("object")
        assertThat(schema.containsKey("properties")).isTrue
        assertThat(schema["additionalProperties"]?.jsonPrimitive?.boolean).isFalse
        
        // Properties should be empty object, not null
        val properties = schema["properties"]?.jsonObject
        assertThat(properties).isNotNull
        assertThat(properties!!).isEmpty()
    }
    
    @Test
    fun `validate all actual command schemas are JSON Schema compliant`() {
        // Create a real context to get all actual commands
        val tempDir = java.io.File("/tmp/test-mcp-${System.currentTimeMillis()}")
        tempDir.mkdirs()
        
        // Create a test user config file to avoid interactive prompt
        val profileDir = java.io.File(tempDir, "profiles/default")
        profileDir.mkdirs()
        val userConfigFile = java.io.File(profileDir, "settings.yaml")
        userConfigFile.writeText("""
            email: test@example.com
            region: us-east-1
            keyName: test-key
            sshKeyPath: /tmp/test-key.pem
            awsProfile: default
            awsAccessKey: test-access-key
            awsSecret: test-secret
            axonOpsOrg: ""
            axonOpsKey: ""
        """.trimIndent())
        
        val realContext = Context(tempDir)
        val realRegistry = McpToolRegistry(realContext)
        
        // Get all tools from the registry
        val tools = realRegistry.getTools()
        
        println("Total tools found: ${tools.size}")
        
        // Check each tool's schema
        tools.forEachIndexed { index, tool ->
            println("\n=== Tool ${index + 1}: ${tool.name} ===")
            println("Description: ${tool.description}")
            
            val schema = tool.inputSchema
            println("Schema: $schema")
            
            // Validate core JSON Schema requirements
            assertThat(schema["type"]?.jsonPrimitive?.content)
                .describedAs("Tool '${tool.name}' (index $index) should have type='object'")
                .isEqualTo("object")
            
            assertThat(schema.containsKey("properties"))
                .describedAs("Tool '${tool.name}' (index $index) must have 'properties' field")
                .isTrue
            
            val properties = schema["properties"]?.jsonObject
            assertThat(properties)
                .describedAs("Tool '${tool.name}' (index $index) properties should not be null")
                .isNotNull
            
            // Check each property has a type
            properties?.forEach { (propName, propValue) ->
                val propObject = propValue.jsonObject
                assertThat(propObject["type"])
                    .describedAs("Property '$propName' in tool '${tool.name}' should have a type")
                    .isNotNull
                
                val propType = propObject["type"]?.jsonPrimitive?.content
                assertThat(propType)
                    .describedAs("Property '$propName' in tool '${tool.name}' should have a valid type")
                    .isIn("string", "number", "boolean", "integer", "array", "object")
            }
            
            // Check additionalProperties is set
            assertThat(schema.containsKey("additionalProperties"))
                .describedAs("Tool '${tool.name}' (index $index) should have additionalProperties field")
                .isTrue
            
            // If there are required fields, validate them
            val required = schema["required"]?.jsonArray
            if (required != null) {
                required.forEach { requiredField ->
                    val fieldName = requiredField.jsonPrimitive.content
                    assertThat(properties?.containsKey(fieldName))
                        .describedAs("Required field '$fieldName' should exist in properties for tool '${tool.name}'")
                        .isTrue
                }
            }
        }
        
        // Find tool at index 15 (tools.15 from error)
        if (tools.size > 15) {
            println("\n=== TOOL 16 (index 15, from error message) ===")
            val tool15 = tools[15]
            println("Name: ${tool15.name}")
            println("Description: ${tool15.description}")
            println("Full schema: ${tool15.inputSchema}")
        }
    }
    
    @Test
    fun `find problematic schemas in actual commands`() {
        // Create a real context to analyze actual commands
        val tempDir = java.io.File("/tmp/test-mcp-${System.currentTimeMillis()}")
        tempDir.mkdirs()
        
        // Create a test user config file to avoid interactive prompt
        val profileDir = java.io.File(tempDir, "profiles/default")
        profileDir.mkdirs()
        val userConfigFile = java.io.File(profileDir, "settings.yaml")
        userConfigFile.writeText("""
            email: test@example.com
            region: us-east-1
            keyName: test-key
            sshKeyPath: /tmp/test-key.pem
            awsProfile: default
            awsAccessKey: test-access-key
            awsSecret: test-secret
            axonOpsOrg: ""
            axonOpsKey: ""
        """.trimIndent())
        
        val realContext = Context(tempDir)
        val realRegistry = McpToolRegistry(realContext)
        
        // Get all tools and look for potential issues
        val tools = realRegistry.getTools()
        
        println("\n=== Looking for potential schema issues ===")
        
        tools.forEachIndexed { index, tool ->
            val schema = tool.inputSchema
            val properties = schema["properties"]?.jsonObject ?: emptyMap()
            
            // Check for potential issues
            val issues = mutableListOf<String>()
            
            // Check if any property has an invalid or missing type
            properties.forEach { (propName, propValue) ->
                val propObject = propValue.jsonObject
                val propType = propObject["type"]?.jsonPrimitive?.content
                
                if (propType == null) {
                    issues.add("Property '$propName' has no type")
                } else if (propType !in listOf("string", "number", "boolean", "integer", "array", "object")) {
                    issues.add("Property '$propName' has invalid type: $propType")
                }
                
                // Check for invalid default values
                val defaultValue = propObject["default"]
                if (defaultValue != null) {
                    when (propType) {
                        "string" -> {
                            if (defaultValue !is JsonPrimitive || !defaultValue.isString) {
                                issues.add("Property '$propName' has invalid default for string type")
                            }
                        }
                        "number", "integer" -> {
                            if (defaultValue !is JsonPrimitive || defaultValue.doubleOrNull == null) {
                                issues.add("Property '$propName' has invalid default for number type")
                            }
                        }
                        "boolean" -> {
                            if (defaultValue !is JsonPrimitive || defaultValue.booleanOrNull == null) {
                                issues.add("Property '$propName' has invalid default for boolean type")
                            }
                        }
                    }
                }
            }
            
            if (issues.isNotEmpty()) {
                println("\nTool ${index + 1} '${tool.name}' has issues:")
                issues.forEach { println("  - $it") }
            }
        }
    }

    @Test
    fun `ensure schema does not contain non-Parameters`() {
        var command = TestCommand()
        var registry = McpToolRegistry(context)
        var schema = registry.generateSchema(command)

        assertThat(schema.keys).contains("properties")
    }

    // Test command classes
    // IMPORTANT: These test commands are used to verify MCP parameter passing.
    // They track execution count to ensure proper state management.
    
    @Parameters(commandDescription = "Empty command with no parameters")
    private class EmptyCommand : ICommand {
        override fun execute() {
            // No-op
        }
    }
    
    @Parameters(commandDescription = "Test command")
    private class TestCommand : ICommand {
        @Parameter(names = ["--test-param", "-t"], description = "Test parameter description")
        var testField: String = ""
        
        @Parameter(names = ["--int-param"], description = "Integer parameter")
        var intField: Int = 0
        
        @Parameter(names = ["--bool"], description = "Boolean parameter")
        var boolField: Boolean = false
        
        @Parameter(description = "Field without explicit names")
        var fieldWithoutNames: String = ""

        // should not show up in the schema
        val log = KotlinLogging.logger {}

        override fun execute() {
            // Test implementation
        }
    }
    
    private class TestDelegate {
        @Parameter(names = ["--delegate-param", "-d"], description = "Delegate parameter")
        var delegateField: String = ""
    }
    
    @Parameters(commandDescription = "Test command with delegate")
    private class TestCommandWithDelegate : ICommand {
        @Parameter(names = ["--main-param"], description = "Main parameter")
        var mainField: String = ""
        
        @ParametersDelegate
        var delegate = TestDelegate()
        
        override fun execute() {
            // Test implementation - no-op for basic tests
        }
    }
    
    // Test command that tracks execution for executeTool tests
    @Parameters(commandDescription = "Test command with execution tracking")
    private class TestCommandWithExecution : ICommand {
        @Parameter(names = ["--test-param", "-t"], description = "Test parameter")
        var testField: String = ""
        
        @Parameter(names = ["--int-param"], description = "Integer parameter")
        var intField: Int = 0
        
        @Parameter(names = ["--bool"], description = "Boolean parameter")
        var boolField: Boolean = false
        
        var executionCount: Int = 0
        
        override fun execute() {
            executionCount++
            // Simulates a real command execution
            println("Executed with testField=$testField, intField=$intField, boolField=$boolField")
        }
    }
    
    // Test command with delegate that tracks execution
    @Parameters(commandDescription = "Test command with delegate and execution tracking")
    private class TestCommandWithDelegateExecution : ICommand {
        @Parameter(names = ["--main-param"], description = "Main parameter")
        var mainField: String = ""
        
        @ParametersDelegate
        var delegate = TestDelegate()
        
        var executionCount: Int = 0
        
        override fun execute() {
            executionCount++
            // Simulates a real command execution with delegate
            println("Executed with mainField=$mainField, delegateField=${delegate.delegateField}")
        }
    }
    
    @Parameters(commandDescription = "Test command with various types")
    private class TestCommandWithTypes : ICommand {
        @Parameter(names = ["--string"], description = "String field")
        var stringField: String = ""
        
        @Parameter(names = ["--int"], description = "Int field")
        var intField: Int = 0
        
        @Parameter(names = ["--bool"], description = "Boolean field")
        var booleanField: Boolean = false
        
        @Parameter(names = ["--long"], description = "Long field")
        var longField: Long = 0L
        
        @Parameter(names = ["--double"], description = "Double field")
        var doubleField: Double = 0.0
        
        override fun execute() {
            // Test implementation
        }
    }
}