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
        
        // Create context with proper user config
        context = Context(tempDir)
        registry = McpToolRegistry(context)
    }
    
    @AfterEach
    fun tearDown() {
        stopKoin()
    }
    
    @Test
    fun `only commands with McpCommand annotation should be registered`() {
        val tools = registry.getTools()
        
        println("Found ${tools.size} tools:")
        tools.forEach { tool ->
            println("  - ${tool.name}: ${tool.description}")
        }
        
        // With only Init having @McpCommand, we should have exactly 1 tool
        assertEquals(1, tools.size, "Should have exactly 1 tool (Init)")
        
        val initTool = tools.find { it.name == "init" }
        assertNotNull(initTool, "Init tool should be present")
        
        // Verify other commands are NOT present
        val excludedCommands = listOf("up", "use", "update-config", "down", "stop", "restart")
        for (cmd in excludedCommands) {
            val tool = tools.find { it.name == cmd }
            assertNull(tool, "$cmd should NOT be present (no @McpCommand annotation)")
        }
    }
    
    @Test
    fun `verify Init command has valid schema`() {
        val tools = registry.getTools()
        assertEquals(1, tools.size)
        
        val initTool = tools[0]
        val schema = initTool.inputSchema
        
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