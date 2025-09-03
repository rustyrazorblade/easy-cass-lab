package com.rustyrazorblade.easycasslab.mcp

import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.di.outputModule
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import java.io.File

class McpAnnotationTest {
    companion object {
        private val log = KotlinLogging.logger {}
    }

    @TempDir
    lateinit var tempDir: File

    private lateinit var context: Context
    private lateinit var registry: McpToolRegistry

    @BeforeEach
    fun setup() {
        // Initialize Koin for dependency injection
        startKoin {
            modules(listOf(outputModule))
        }

        // Create a proper settings file
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

        // Create context with proper user config
        context = Context(tempDir)
        registry = McpToolRegistry(context)
    }

    @AfterEach
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `verify commands with McpCommand annotation are registered`() {
        val tools = registry.getTools()

        println("Found ${tools.size} tools:")
        tools.forEach { tool ->
            println("  - ${tool.name}: ${tool.description}")
        }

        // We should have at least one tool
        assertTrue(tools.size > 0, "Should have at least one tool registered")

        // Verify that commands with @McpCommand are present
        val initTool = tools.find { it.name == "init" }
        assertNotNull(initTool, "Init tool should be present")
    }

    @Test
    fun `verify Init command has valid schema`() {
        val tools = registry.getTools()
        assertTrue(tools.size > 0, "Should have at least one tool")

        val initTool = tools.find { it.name == "init" }
        assertNotNull(initTool, "Init tool should be present")

        val schema = initTool!!.inputSchema

        // Schema now contains properties directly (MCP SDK adds the wrapper)
        // Check that we have some properties
        assertTrue(schema.size > 0, "Schema should have properties")

        // Check a few expected properties exist
        assertNotNull(schema["cassandraInstances"], "Should have cassandraInstances property")
        assertNotNull(schema["instanceType"], "Should have instanceType property")

        // Print the schema for debugging
        println("Init tool schema:")
        println(schema.toString())
    }
}
