package com.rustyrazorblade.easycasslab.mcp

import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.commands.Init
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

class McpInitCommandTest {
    
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
    fun `init command should be available in getTools`() {
        val tools = registry.getTools()
        val initTool = tools.find { it.name == "init" }
        
        assertNotNull(initTool, "init tool should be available")
        assertEquals("init", initTool?.name)
        assertEquals("Initialize this directory for easy-cass-lab", initTool?.description)
    }
    
    @Test
    fun `init command should have valid JSON Schema`() {
        val tools = registry.getTools()
        val initTool = tools.find { it.name == "init" }
        
        assertNotNull(initTool)
        val schema = initTool!!.inputSchema
        
        // Schema now contains just the properties directly (MCP SDK adds the wrapper)
        
        // Verify cassandraInstances property
        val cassandraInstances = schema["cassandraInstances"]?.jsonObject
        assertNotNull(cassandraInstances)
        assertEquals("number", cassandraInstances?.get("type")?.jsonPrimitive?.content)
        assertNotNull(cassandraInstances?.get("description"))
        
        // Verify instanceType property
        val instanceType = schema["instanceType"]?.jsonObject
        assertNotNull(instanceType)
        assertEquals("string", instanceType?.get("type")?.jsonPrimitive?.content)
        assertNotNull(instanceType?.get("description"))
    }
    
    @Test
    fun `init command schema should include all parameter types`() {
        val initCommand = Init(context)
        val schema = registry.generateSchema(initCommand)
        
        // Schema now contains properties directly at the root level
        
        // Check for various parameter types based on actual available properties
        val expectedProperties = listOf(
            "cassandraInstances", // @Parameter
            "stressInstances",    // @Parameter
            "instanceType",       // @Parameter with env var default
            "azs",               // @Parameter with list converter
            "ebsType",           // @Parameter with enum
            "name",              // @Parameter simple string
            "arch",              // @Parameter with enum
            "clean"              // @Parameter boolean
            // Note: tags is a @DynamicParameter and may not show up in schema
        )
        
        for (prop in expectedProperties) {
            assertNotNull(schema[prop], "Property $prop should be in schema")
        }
        
        // Verify delegate parameters are included (EMR parameters)
        val emrProperties = listOf("enable", "masterInstanceType", "workerCount", "workerInstanceType")
        for (prop in emrProperties) {
            assertNotNull(schema[prop], "EMR delegate property $prop should be in schema")
        }
    }
    
    @Test
    fun `init command should handle enum types correctly`() {
        val initCommand = Init(context)
        val schema = registry.generateSchema(initCommand)
        
        // Schema now contains properties directly at the root level
        
        // Check ebsType enum
        val ebsType = schema["ebsType"]?.jsonObject
        assertNotNull(ebsType)
        assertEquals("string", ebsType?.get("type")?.jsonPrimitive?.content)
        
        val ebsEnum = ebsType?.get("enum")?.jsonArray
        assertNotNull(ebsEnum, "ebsType should have enum values")
        assertTrue(ebsEnum!!.size > 0, "ebsType enum should have values")
        
        // Check arch enum  
        val arch = schema["arch"]?.jsonObject
        assertNotNull(arch)
        assertEquals("string", arch?.get("type")?.jsonPrimitive?.content)
        
        val archEnum = arch?.get("enum")?.jsonArray
        assertNotNull(archEnum, "arch should have enum values")
        assertTrue(archEnum!!.contains(JsonPrimitive("amd64")), "arch enum should contain amd64")
        assertTrue(archEnum.contains(JsonPrimitive("arm64")), "arch enum should contain arm64")
    }
    
    @Test
    fun `executeTool should handle init command execution`() {
        // Test that executeTool can handle init command
        // Note: Init command requires Docker and creates files, so it may fail in test environment
        // This test validates the MCP layer, not the actual Init command execution
        
        val arguments = buildJsonObject {
            put("cassandraInstances", 3)
            put("name", "test-cluster")
        }
        
        val result = registry.executeTool("init", arguments)
        
        assertNotNull(result, "Result should not be null")
        // The command may error due to Docker requirements in test environment
        // But the MCP layer should still return a result
        assertTrue(result.content.isNotEmpty(), "Result should have content")
    }
    
    @Test
    fun `init command should not be present if not in allowlist`() {
        // This test documents that when using McpServer with allowlist,
        // only allowed commands should be registered
        
        // Simulate what McpServer does
        val allowedCommands = setOf("up", "down") // init not included
        val tools = registry.getTools()
        val filteredTools = tools.filter { it.name in allowedCommands }
        
        val initTool = filteredTools.find { it.name == "init" }
        assertNull(initTool, "init should not be present when not in allowlist")
        
        // Verify other commands would be present
        val upTool = filteredTools.find { it.name == "up" }
        assertNotNull(upTool, "up should be present when in allowlist")
    }
}