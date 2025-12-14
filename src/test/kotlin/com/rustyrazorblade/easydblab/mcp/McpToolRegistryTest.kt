package com.rustyrazorblade.easydblab.mcp

import com.rustyrazorblade.easydblab.BaseKoinTest
import com.rustyrazorblade.easydblab.annotations.McpCommand
import com.rustyrazorblade.easydblab.commands.PicoCommand
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import picocli.CommandLine.Command
import picocli.CommandLine.Mixin
import picocli.CommandLine.Option

/**
 * Tests for McpToolRegistry which discovers and executes MCP tools.
 */
class McpToolRegistryTest : BaseKoinTest() {
    private val registry: McpToolRegistry by lazy { McpToolRegistry(context) }

    // Test commands for argument mapping verification
    @Command(name = "test-string-mapping")
    @McpCommand
    class StringMappingCommand : PicoCommand {
        @Option(names = ["--name"], description = ["A name"])
        var name: String = ""

        var wasExecuted = false

        override fun execute() {
            wasExecuted = true
        }
    }

    @Command(name = "test-int-mapping")
    @McpCommand
    class IntMappingCommand : PicoCommand {
        @Option(names = ["--count"], description = ["A count"])
        var count: Int = 0

        var wasExecuted = false

        override fun execute() {
            wasExecuted = true
        }
    }

    @Command(name = "test-boolean-mapping")
    @McpCommand
    class BooleanMappingCommand : PicoCommand {
        @Option(names = ["--enabled"], description = ["Enable flag"])
        var enabled: Boolean = false

        var wasExecuted = false

        override fun execute() {
            wasExecuted = true
        }
    }

    enum class TestMappingEnum {
        ALPHA,
        BETA,
    }

    @Command(name = "test-enum-mapping")
    @McpCommand
    class EnumMappingCommand : PicoCommand {
        @Option(names = ["--mode"], description = ["Mode selection"])
        var mode: TestMappingEnum = TestMappingEnum.ALPHA

        var wasExecuted = false

        override fun execute() {
            wasExecuted = true
        }
    }

    class SharedMappingOptions {
        @Option(names = ["--shared-opt"], description = ["Shared option"])
        var sharedOpt: String = ""
    }

    @Command(name = "test-mixin-mapping")
    @McpCommand
    class MixinMappingCommand : PicoCommand {
        @Mixin
        val shared = SharedMappingOptions()

        @Option(names = ["--direct-opt"], description = ["Direct option"])
        var directOpt: String = ""

        var wasExecuted = false

        override fun execute() {
            wasExecuted = true
        }
    }

    @Nested
    inner class ToolDiscovery {
        @Test
        fun `should create tool specifications from registry`() {
            val tools = registry.getToolSpecifications()

            assertThat(tools).isNotNull
        }

        @Test
        fun `tool specifications should have names`() {
            val tools = registry.getToolSpecifications()

            tools.forEach { spec ->
                assertThat(spec.tool().name()).isNotBlank
            }
        }

        @Test
        fun `tool specifications should have descriptions`() {
            val tools = registry.getToolSpecifications()

            tools.forEach { spec ->
                assertThat(spec.tool().description()).isNotBlank
            }
        }
    }

    @Nested
    inner class ToolSpecificationCreation {
        @Test
        fun `should create specification with correct name`() {
            val tools = registry.getToolSpecifications()

            if (tools.isNotEmpty()) {
                val firstTool = tools.first()
                assertThat(firstTool.tool().name()).isNotBlank
                assertThat(firstTool.tool().name()).doesNotContain(" ")
            }
        }

        @Test
        fun `should create specification with input schema`() {
            val tools = registry.getToolSpecifications()

            tools.forEach { spec ->
                assertThat(spec.tool().inputSchema()).isNotNull
            }
        }
    }

    @Nested
    inner class ArgumentMapping {
        @Test
        fun `should handle string argument type`() {
            val generator = McpSchemaGenerator()
            val schema = generator.generateSchema(StringMappingCommand())

            assertThat(schema).contains(""""name":""")
            assertThat(schema).contains(""""type": "string"""")
        }

        @Test
        fun `should handle integer argument type`() {
            val generator = McpSchemaGenerator()
            val schema = generator.generateSchema(IntMappingCommand())

            assertThat(schema).contains(""""count":""")
            assertThat(schema).contains(""""type": "integer"""")
        }

        @Test
        fun `should handle boolean argument type`() {
            val generator = McpSchemaGenerator()
            val schema = generator.generateSchema(BooleanMappingCommand())

            assertThat(schema).contains(""""enabled":""")
            assertThat(schema).contains(""""type": "boolean"""")
        }

        @Test
        fun `should handle enum argument type`() {
            val generator = McpSchemaGenerator()
            val schema = generator.generateSchema(EnumMappingCommand())

            assertThat(schema).contains(""""mode":""")
            assertThat(schema).contains(""""type": "string"""")
            assertThat(schema).contains(""""enum":""")
        }

        @Test
        fun `should handle mixin arguments`() {
            val generator = McpSchemaGenerator()
            val schema = generator.generateSchema(MixinMappingCommand())

            assertThat(schema).contains(""""directOpt":""")
            assertThat(schema).contains(""""sharedOpt":""")
        }
    }

    @Nested
    inner class SyncToolSpecificationContract {
        @Test
        fun `SyncToolSpecification should be callable`() {
            val tools = registry.getToolSpecifications()

            tools.forEach { spec ->
                assertThat(spec).isInstanceOf(SyncToolSpecification::class.java)
            }
        }

        @Test
        fun `tool specifications should return valid tool objects`() {
            val tools = registry.getToolSpecifications()

            tools.forEach { spec ->
                val tool = spec.tool()
                assertThat(tool.name()).isNotNull
                assertThat(tool.description()).isNotNull
                assertThat(tool.inputSchema()).isNotNull
            }
        }
    }

    @Nested
    inner class ToolNaming {
        @Test
        fun `tool names should be kebab-case or valid identifiers`() {
            val tools = registry.getToolSpecifications()

            tools.forEach { spec ->
                val name = spec.tool().name()
                assertThat(name).doesNotContain(" ")
                assertThat(name).matches("[a-z0-9_-]+")
            }
        }
    }
}
