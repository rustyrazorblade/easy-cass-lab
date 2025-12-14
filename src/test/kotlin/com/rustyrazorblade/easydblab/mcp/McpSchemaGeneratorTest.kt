package com.rustyrazorblade.easydblab.mcp

import com.rustyrazorblade.easydblab.BaseKoinTest
import com.rustyrazorblade.easydblab.commands.PicoCommand
import kotlinx.serialization.json.JsonPrimitive
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import picocli.CommandLine.Command
import picocli.CommandLine.Mixin
import picocli.CommandLine.Option

/**
 * Tests for McpSchemaGenerator which generates JSON Schema from PicoCLI annotations.
 */
class McpSchemaGeneratorTest : BaseKoinTest() {
    private val generator = McpSchemaGenerator()

    // Test commands used across multiple tests
    @Command(name = "empty-command")
    class EmptyCommand : PicoCommand {
        override fun execute() {}
    }

    @Command(name = "string-option-command")
    class StringOptionCommand : PicoCommand {
        @Option(names = ["--name"], description = ["The name to use"])
        var name: String = ""

        override fun execute() {}
    }

    @Command(name = "int-option-command")
    class IntOptionCommand : PicoCommand {
        @Option(names = ["--count"], description = ["Number of items"])
        var count: Int = 0

        override fun execute() {}
    }

    @Command(name = "long-option-command")
    class LongOptionCommand : PicoCommand {
        @Option(names = ["--size"], description = ["Size in bytes"])
        var size: Long = 0L

        override fun execute() {}
    }

    @Command(name = "boolean-option-command")
    class BooleanOptionCommand : PicoCommand {
        @Option(names = ["--enabled"], description = ["Enable the feature"])
        var enabled: Boolean = false

        override fun execute() {}
    }

    @Command(name = "double-option-command")
    class DoubleOptionCommand : PicoCommand {
        @Option(names = ["--ratio"], description = ["The ratio value"])
        var ratio: Double = 0.0

        override fun execute() {}
    }

    @Command(name = "required-option-command")
    class RequiredOptionCommand : PicoCommand {
        @Option(names = ["--id"], required = true, description = ["Required ID"])
        var id: String = ""

        override fun execute() {}
    }

    enum class TestEnum {
        ALPHA,
        BETA,
        GAMMA,
    }

    @Command(name = "enum-option-command")
    class EnumOptionCommand : PicoCommand {
        @Option(names = ["--mode"], description = ["Operating mode"])
        var mode: TestEnum = TestEnum.ALPHA

        override fun execute() {}
    }

    enum class TypedEnum(
        private val type: String,
    ) {
        SMALL("small"),
        MEDIUM("medium"),
        LARGE("large"),
        ;

        fun getType(): String = type
    }

    @Command(name = "typed-enum-command")
    class TypedEnumCommand : PicoCommand {
        @Option(names = ["--size"], description = ["Size selection"])
        var size: TypedEnum = TypedEnum.SMALL

        override fun execute() {}
    }

    class SharedOptions {
        @Option(names = ["--verbose"], description = ["Enable verbose output"])
        var verbose: Boolean = false

        @Option(names = ["--output"], description = ["Output file path"])
        var output: String = ""
    }

    @Command(name = "mixin-command")
    class MixinCommand : PicoCommand {
        @Mixin
        val sharedOptions = SharedOptions()

        @Option(names = ["--input"], description = ["Input file path"])
        var input: String = ""

        override fun execute() {}
    }

    @Command(name = "default-value-command")
    class DefaultValueCommand : PicoCommand {
        @Option(names = ["--timeout"], description = ["Timeout in seconds"])
        var timeout: Int = 30

        @Option(names = ["--host"], description = ["Target host"])
        var host: String = "localhost"

        @Option(names = ["--debug"], description = ["Debug mode"])
        var debug: Boolean = true

        override fun execute() {}
    }

    @Command(name = "multiple-options-command")
    class MultipleOptionsCommand : PicoCommand {
        @Option(names = ["--name"], description = ["Name value"])
        var name: String = ""

        @Option(names = ["--count"], description = ["Count value"])
        var count: Int = 0

        @Option(names = ["--enabled"], description = ["Enabled flag"])
        var enabled: Boolean = false

        override fun execute() {}
    }

    @Command(name = "multiple-required-command")
    class MultipleRequiredCommand : PicoCommand {
        @Option(names = ["--first"], required = true, description = ["First required"])
        var first: String = ""

        @Option(names = ["--second"], required = true, description = ["Second required"])
        var second: String = ""

        @Option(names = ["--optional"], description = ["Optional field"])
        var optional: String = ""

        override fun execute() {}
    }

    @Nested
    inner class EmptySchema {
        @Test
        fun `should generate empty schema for command with no options`() {
            val schema = generator.generateSchema(EmptyCommand())

            assertThatSchema(schema)
                .isObjectType()
                .hasNoProperties()
                .hasNoRequiredFields()
        }
    }

    @Nested
    inner class TypeMapping {
        @Test
        fun `should map String option to string type`() {
            val schema = generator.generateSchema(StringOptionCommand())

            assertThatSchema(schema)
                .hasProperty("name")
                .withType("string")
                .withDescription("The name to use")
        }

        @Test
        fun `should map Int option to integer type`() {
            val schema = generator.generateSchema(IntOptionCommand())

            assertThatSchema(schema)
                .hasProperty("count")
                .withType("integer")
        }

        @Test
        fun `should map Long option to integer type`() {
            val schema = generator.generateSchema(LongOptionCommand())

            assertThatSchema(schema)
                .hasProperty("size")
                .withType("integer")
        }

        @Test
        fun `should map Boolean option to boolean type`() {
            val schema = generator.generateSchema(BooleanOptionCommand())

            assertThatSchema(schema)
                .hasProperty("enabled")
                .withType("boolean")
        }

        @Test
        fun `should map Double option to number type`() {
            val schema = generator.generateSchema(DoubleOptionCommand())

            assertThatSchema(schema)
                .hasProperty("ratio")
                .withType("number")
        }
    }

    @Nested
    inner class RequiredOptions {
        @Test
        fun `should mark required option in required array`() {
            val schema = generator.generateSchema(RequiredOptionCommand())

            assertThatSchema(schema).hasRequiredField("id")
        }

        @Test
        fun `should include multiple required options in required array`() {
            val schema = generator.generateSchema(MultipleRequiredCommand())

            assertThatSchema(schema).hasRequiredFields("first", "second")
        }

        @Test
        fun `should not include required array for optional-only commands`() {
            val schema = generator.generateSchema(StringOptionCommand())

            assertThatSchema(schema).hasNoRequiredFields()
        }
    }

    @Nested
    inner class EnumOptions {
        @Test
        fun `should include enum values in schema`() {
            val schema = generator.generateSchema(EnumOptionCommand())

            assertThatSchema(schema)
                .hasProperty("mode")
                .withType("string")
                .withEnumValues("alpha", "beta", "gamma")
        }

        @Test
        fun `should use getType method for typed enums`() {
            val schema = generator.generateSchema(TypedEnumCommand())

            assertThatSchema(schema)
                .hasProperty("size")
                .withEnumValues("small", "medium", "large")
        }
    }

    @Nested
    inner class MixinProcessing {
        @Test
        fun `should include options from mixin`() {
            val schema = generator.generateSchema(MixinCommand())

            assertThatSchema(schema).hasProperties("input", "verbose", "output")
        }

        @Test
        fun `should have correct types for mixin options`() {
            val schema = generator.generateSchema(MixinCommand())

            assertThatSchema(schema).hasProperty("verbose").withType("boolean")
            assertThatSchema(schema).hasProperty("output").withType("string")
            assertThatSchema(schema).hasProperty("input").withType("string")
        }
    }

    @Nested
    inner class DefaultValues {
        @Test
        fun `should include default values in schema`() {
            val schema = generator.generateSchema(DefaultValueCommand())

            assertThatSchema(schema).hasProperty("timeout").withDefault(JsonPrimitive(30))
            assertThatSchema(schema).hasProperty("host").withDefault(JsonPrimitive("localhost"))
            assertThatSchema(schema).hasProperty("debug").withDefault(JsonPrimitive(true))
        }

        @Test
        fun `should not include default for empty strings`() {
            val schema = generator.generateSchema(StringOptionCommand())

            assertThatSchema(schema).hasProperty("name").withNoDefault()
        }
    }

    @Nested
    inner class MultipleOptions {
        @Test
        fun `should generate schema with multiple options`() {
            val schema = generator.generateSchema(MultipleOptionsCommand())

            assertThatSchema(schema).hasProperties("name", "count", "enabled")
        }

        @Test
        fun `should have correct structure`() {
            val schema = generator.generateSchema(MultipleOptionsCommand())

            assertThatSchema(schema)
                .isObjectType()
                .hasPropertyCount(3)
        }
    }

    @Nested
    inner class JsonSerialization {
        @Test
        fun `toJson should produce valid JSON`() {
            val schema = generator.generateSchema(StringOptionCommand())
            val json = schema.toJson()

            assertThat(json).startsWith("{")
            assertThat(json).endsWith("}")
            assertThat(json).contains("\"type\":\"object\"")
        }
    }
}
