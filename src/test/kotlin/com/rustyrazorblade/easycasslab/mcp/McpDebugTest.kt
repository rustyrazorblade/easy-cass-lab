package com.rustyrazorblade.easycasslab.mcp

import com.rustyrazorblade.easycasslab.Context
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File

class McpDebugTest {
    
    @Test
    fun `debug tool 15 schema`() {
        // Create a real context with mocked userConfig to avoid NPE
        val mockUserConfig = com.rustyrazorblade.easycasslab.configuration.User(
            email = "test@example.com",
            region = "us-east-1",
            keyName = "test-key",
            sshKeyPath = "/tmp/test-key.pem",
            awsProfile = "default",
            awsAccessKey = "test-access-key",
            awsSecret = "test-secret"
        )
        
        val context = Context(File("/tmp"))
        
        // Use reflection to set userConfig
        val userConfigField = Context::class.java.getDeclaredField("userConfig")
        userConfigField.isAccessible = true
        userConfigField.set(context, mockUserConfig)
        
        val registry = McpToolRegistry(context)
        val tools = registry.getTools()
        
        println("Total tools: ${tools.size}")
        
        // Check tool 15 specifically
        if (tools.size > 15) {
            val tool15 = tools[15]
            println("\n=== TOOL 15 (${tool15.name}) ===")
            println("Description: ${tool15.description}")
            
            val schema = tool15.inputSchema
            println("\nRaw schema:")
            println(Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), schema))
            
            // Validate schema structure
            println("\nSchema validation:")
            println("- Has 'type': ${schema.containsKey("type")}")
            println("- Type value: ${schema["type"]?.jsonPrimitive?.content}")
            println("- Has 'properties': ${schema.containsKey("properties")}")
            println("- Has 'additionalProperties': ${schema.containsKey("additionalProperties")}")
            
            val properties = schema["properties"]?.jsonObject
            if (properties != null) {
                println("\nProperties:")
                properties.forEach { (key, value) ->
                    val prop = value.jsonObject
                    println("  $key:")
                    println("    - type: ${prop["type"]?.jsonPrimitive?.content}")
                    println("    - description: ${prop["description"]?.jsonPrimitive?.content}")
                    println("    - default: ${prop["default"]}")
                    println("    - enum: ${prop["enum"]}")
                }
            }
            
            // Check for any potential issues
            println("\nPotential issues:")
            
            // Check if any property is missing 'type'
            properties?.forEach { (key, value) ->
                val prop = value.jsonObject
                if (!prop.containsKey("type")) {
                    println("  ERROR: Property '$key' is missing 'type' field")
                }
            }
            
            // Check for nested objects or arrays
            properties?.forEach { (key, value) ->
                val prop = value.jsonObject
                val type = prop["type"]?.jsonPrimitive?.content
                if (type == "object" || type == "array") {
                    println("  WARNING: Property '$key' has complex type '$type'")
                }
            }
        } else {
            println("Tool 15 not found (only ${tools.size} tools available)")
        }
        
        // Also print all tool names to see the full list
        println("\n=== ALL TOOLS ===")
        tools.forEachIndexed { index, tool ->
            println("$index: ${tool.name}")
        }
    }
}