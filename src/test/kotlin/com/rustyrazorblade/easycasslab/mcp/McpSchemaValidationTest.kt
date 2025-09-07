package com.rustyrazorblade.easycasslab.mcp

import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.di.KoinModules
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import java.io.File

class McpSchemaValidationTest {
    @BeforeEach
    fun setup() {
        // Initialize Koin for dependency injection
        startKoin {
            modules(KoinModules.getAllModules())
        }
    }

    @AfterEach
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `check for JSON Schema 2020-12 compliance issues`() {
        // Create a context with a temp directory
        val tempDir = File("/tmp/test-mcp-${System.currentTimeMillis()}")
        tempDir.mkdirs()

        // Create a test user config file to avoid interactive prompt
        val profileDir = File(tempDir, "profiles/default")
        profileDir.mkdirs()
        val userConfigFile = File(profileDir, "settings.yaml")
        userConfigFile.writeText(
            """
            email: test@example.com
            region: us-east-1
            keyName: test-key
            sshKeyPath: /tmp/test-key.pem
            awsProfile: default
            awsAccessKey: test-access-key
            awsSecret: test-secret
            axonOpsOrg: ""
            axonOpsKey: ""
            """.trimIndent(),
        )

        val context = Context(tempDir)

        val registry = McpToolRegistry(context)
        val tools = registry.getTools()

        println("Checking all ${tools.size} tools for JSON Schema 2020-12 compliance issues...\n")

        var issueCount = 0

        tools.forEachIndexed { index, tool ->
            val issues = mutableListOf<String>()
            val schema = tool.inputSchema

            // Check 1: Must have type field at root
            if (!schema.containsKey("type")) {
                issues.add("Missing root 'type' field")
            }

            // Check 2: Must have properties field (even if empty)
            if (!schema.containsKey("properties")) {
                issues.add("Missing 'properties' field")
            }

            // Check 3: additionalProperties should be boolean, not string
            val additionalProps = schema["additionalProperties"]
            if (additionalProps != null && additionalProps !is JsonPrimitive) {
                issues.add("additionalProperties must be a boolean primitive")
            }

            val properties = schema["properties"]?.jsonObject
            if (properties != null) {
                properties.forEach { (propName, propValue) ->
                    val prop = propValue.jsonObject

                    // Check 4: Each property must have a type
                    if (!prop.containsKey("type")) {
                        issues.add("Property '$propName' missing 'type' field")
                    }

                    // Check 5: If enum is present, it must be an array
                    val enumField = prop["enum"]
                    if (enumField != null && enumField !is JsonArray) {
                        issues.add("Property '$propName' has invalid 'enum' field (must be array)")
                    }

                    // Check 6: Default value must match the type
                    val type = prop["type"]?.jsonPrimitive?.content
                    val defaultValue = prop["default"]
                    if (defaultValue != null && type != null) {
                        when (type) {
                            "string" -> {
                                if (defaultValue !is JsonPrimitive || !defaultValue.isString) {
                                    issues.add("Property '$propName' has non-string default for string type")
                                }
                            }
                            "number" -> {
                                if (defaultValue !is JsonPrimitive || defaultValue.doubleOrNull == null) {
                                    issues.add("Property '$propName' has non-number default for number type")
                                }
                            }
                            "boolean" -> {
                                if (defaultValue !is JsonPrimitive || defaultValue.booleanOrNull == null) {
                                    issues.add("Property '$propName' has non-boolean default for boolean type")
                                }
                            }
                        }
                    }

                    // Check 7: If enum exists, default must be one of the enum values
                    if (enumField is JsonArray && defaultValue != null) {
                        val enumValues = enumField.map { it.jsonPrimitive.content }
                        val defaultStr = defaultValue.jsonPrimitive.content
                        if (defaultStr !in enumValues) {
                            issues.add("Property '$propName' default '$defaultStr' not in enum values: $enumValues")
                        }
                    }

                    // Check 8: Look for nested objects that might need $ref or proper definitions
                    if (type == "object" && !prop.containsKey("properties")) {
                        issues.add("Property '$propName' is type 'object' but missing 'properties' definition")
                    }

                    // Check 9: Description should be a string
                    val description = prop["description"]
                    if (description != null && (description !is JsonPrimitive || !description.isString)) {
                        issues.add("Property '$propName' has non-string description")
                    }
                }
            }

            // Check 10: If required field exists, it must be an array of strings
            val required = schema["required"]
            if (required != null) {
                if (required !is JsonArray) {
                    issues.add("'required' field must be an array")
                } else {
                    required.forEach { item ->
                        if (item !is JsonPrimitive || !item.isString) {
                            issues.add("'required' array contains non-string value")
                        }
                        // Check that required fields actually exist in properties
                        val reqField = item.jsonPrimitive.content
                        if (properties != null && !properties.containsKey(reqField)) {
                            issues.add("Required field '$reqField' not found in properties")
                        }
                    }
                }
            }

            if (issues.isNotEmpty()) {
                println("Tool $index (${tool.name}) - ${issues.size} issues:")
                issues.forEach { println("  - $it") }
                println()
                issueCount++

                // Print the problematic schema for debugging
                if (index == 15) {
                    println("Full schema for tool 15:")
                    println(Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), schema))
                    println()
                }
            }
        }

        if (issueCount == 0) {
            println("✓ All tools pass JSON Schema 2020-12 compliance checks")
        } else {
            println("Found issues in $issueCount tools")
        }
    }
}
