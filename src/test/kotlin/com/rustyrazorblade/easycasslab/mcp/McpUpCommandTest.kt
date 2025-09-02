package com.rustyrazorblade.easycasslab.mcp

import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.commands.Up
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

class McpUpCommandTest {
    
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
    fun `up command should be available in getTools`() {
        val tools = registry.getTools()
        val upTool = tools.find { it.name == "up" }
        
        assertNotNull(upTool, "up tool should be available")
        assertEquals("up", upTool?.name)
        assertEquals("Starts instances", upTool?.description)
    }
    
    @Test
    fun `up command should have valid JSON Schema`() {
        val tools = registry.getTools()
        val upTool = tools.find { it.name == "up" }
        
        assertNotNull(upTool)
        val schema = upTool!!.inputSchema
        
        // Schema now contains properties directly at the root level (MCP SDK adds the wrapper)
        
        // Up command has noSetup parameter
        val noSetup = schema["noSetup"]?.jsonObject
        assertNotNull(noSetup)
        assertEquals("boolean", noSetup?.get("type")?.jsonPrimitive?.content)
        
        // Up command has hostList parameter from Hosts delegate
        val hostList = schema["hostList"]?.jsonObject
        assertNotNull(hostList)
        assertEquals("string", hostList?.get("type")?.jsonPrimitive?.content)
    }
    
    @Test
    fun `up command schema should have correct parameters`() {
        val upCommand = Up(context)
        val schema = registry.generateSchema(upCommand)
        
        // Schema now contains properties directly at the root level
        
        // Check noSetup property
        val noSetup = schema["noSetup"]?.jsonObject
        assertNotNull(noSetup, "noSetup property should be in schema")
        assertEquals("boolean", noSetup?.get("type")?.jsonPrimitive?.content)
        
        // Check hostList property from delegate
        val hostList = schema["hostList"]?.jsonObject
        assertNotNull(hostList, "hostList property should be in schema")
        assertEquals("string", hostList?.get("type")?.jsonPrimitive?.content)
        
        // Note: hostList may not have a default in the schema since it's initialized to empty string in code
    }
    
    @Test
    fun `executeTool should handle up command execution`() {
        // Test that executeTool can handle up command
        // Note: Up command requires terraform state and Docker, so it may fail in test environment
        // This test validates the MCP layer, not the actual Up command execution
        
        val arguments = buildJsonObject {
            put("hostList", "all")
            put("noSetup", false)
        }
        
        val result = registry.executeTool("up", arguments)
        
        assertNotNull(result, "Result should not be null")
        // The command may error due to terraform requirements in test environment
        // But the MCP layer should still return a result
        assertTrue(result.content.isNotEmpty(), "Result should have content")
    }
    
    @Test
    fun `executeTool should handle up command with no arguments`() {
        // Test that executeTool can handle up command with no arguments (uses defaults)
        
        val result = registry.executeTool("up", null)
        
        assertNotNull(result, "Result should not be null")
        assertTrue(result.content.isNotEmpty(), "Result should have content")
    }
    
    @Test
    fun `up command should be filtered when not in allowlist`() {
        // This test documents that when using McpServer with allowlist,
        // only allowed commands should be registered
        
        // Simulate what McpServer does
        val allowedCommands = setOf("init", "down") // up not included
        val tools = registry.getTools()
        val filteredTools = tools.filter { it.name in allowedCommands }
        
        val upTool = filteredTools.find { it.name == "up" }
        assertNull(upTool, "up should not be present when not in allowlist")
        
        // Verify init would be present
        val initTool = filteredTools.find { it.name == "init" }
        assertNotNull(initTool, "init should be present when in allowlist")
    }
    
    @Test
    fun `up command should be included when in allowlist`() {
        // Simulate what McpServer does with up in allowlist
        val allowedCommands = setOf("init", "up")
        val tools = registry.getTools()
        val filteredTools = tools.filter { it.name in allowedCommands }
        
        val upTool = filteredTools.find { it.name == "up" }
        assertNotNull(upTool, "up should be present when in allowlist")
        assertEquals("up", upTool?.name)
    }
}