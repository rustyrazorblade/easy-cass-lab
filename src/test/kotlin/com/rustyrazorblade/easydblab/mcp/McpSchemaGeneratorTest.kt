package com.rustyrazorblade.easydblab.mcp

import com.rustyrazorblade.easydblab.BaseKoinTest
import com.rustyrazorblade.easydblab.commands.PicoCommand
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

    @Command(name = "special-chars-command")
    class SpecialCharsCommand : PicoCommand {
        @Option(names = ["--message"], description = ["Message with \"quotes\" and newlines\nfor testing"])
        var message: String = ""

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

            assertThat(schema).contains(""""type":"object"""")
            assertThat(schema).contains(""""properties":{}""")
        }
    }

    @Nested
    inner class TypeMapping {
        @Test
        fun `should map String option to string type`() {
            val schema = generator.generateSchema(StringOptionCommand())

            assertThat(schema).contains(""""name":""")
            assertThat(schema).contains(""""type":"string"""")
            assertThat(schema).contains(""""description":"The name to use"""")
        }

        @Test
        fun `should map Int option to integer type`() {
            val schema = generator.generateSchema(IntOptionCommand())

            assertThat(schema).contains(""""count":""")
            assertThat(schema).contains(""""type":"integer"""")
        }

        @Test
        fun `should map Long option to integer type`() {
            val schema = generator.generateSchema(LongOptionCommand())

            assertThat(schema).contains(""""size":""")
            assertThat(schema).contains(""""type":"integer"""")
        }

        @Test
        fun `should map Boolean option to boolean type`() {
            val schema = generator.generateSchema(BooleanOptionCommand())

            assertThat(schema).contains(""""enabled":""")
            assertThat(schema).contains(""""type":"boolean"""")
        }

        @Test
        fun `should map Double option to number type`() {
            val schema = generator.generateSchema(DoubleOptionCommand())

            assertThat(schema).contains(""""ratio":""")
            assertThat(schema).contains(""""type":"number"""")
        }
    }

    @Nested
    inner class RequiredOptions {
        @Test
        fun `should mark required option in required array`() {
            val schema = generator.generateSchema(RequiredOptionCommand())

            assertThat(schema).contains(""""required":["id"]""")
        }

        @Test
        fun `should include multiple required options in required array`() {
            val schema = generator.generateSchema(MultipleRequiredCommand())

            assertThat(schema).contains(""""required":""")
            assertThat(schema).contains("first")
            assertThat(schema).contains("second")
        }

        @Test
        fun `should not include required array for optional-only commands`() {
            val schema = generator.generateSchema(StringOptionCommand())

            assertThat(schema).doesNotContain(""""required":""")
        }
    }

    @Nested
    inner class EnumOptions {
        @Test
        fun `should include enum values in schema`() {
            val schema = generator.generateSchema(EnumOptionCommand())

            assertThat(schema).contains(""""mode":""")
            assertThat(schema).contains(""""type":"string"""")
            assertThat(schema).contains(""""enum":""")
            assertThat(schema).contains("alpha")
            assertThat(schema).contains("beta")
            assertThat(schema).contains("gamma")
        }

        @Test
        fun `should use getType method for typed enums`() {
            val schema = generator.generateSchema(TypedEnumCommand())

            assertThat(schema).contains(""""size":""")
            assertThat(schema).contains(""""enum":""")
            assertThat(schema).contains("small")
            assertThat(schema).contains("medium")
            assertThat(schema).contains("large")
        }
    }

    @Nested
    inner class MixinProcessing {
        @Test
        fun `should include options from mixin`() {
            val schema = generator.generateSchema(MixinCommand())

            assertThat(schema).contains(""""input":""")
            assertThat(schema).contains(""""verbose":""")
            assertThat(schema).contains(""""output":""")
        }

        @Test
        fun `should have correct types for mixin options`() {
            val schema = generator.generateSchema(MixinCommand())

            assertThat(schema).contains(""""verbose":""")
            assertThat(schema).contains(""""type":"boolean"""")
            assertThat(schema).contains(""""type":"string"""")
        }
    }

    @Nested
    inner class DefaultValues {
        @Test
        fun `should include default values in schema`() {
            val schema = generator.generateSchema(DefaultValueCommand())

            assertThat(schema).contains(""""default":30""")
            assertThat(schema).contains(""""default":"localhost"""")
            assertThat(schema).contains(""""default":true""")
        }

        @Test
        fun `should not include default for empty strings`() {
            val schema = generator.generateSchema(StringOptionCommand())

            assertThat(schema).doesNotContain(""""default":""""")
        }
    }

    @Nested
    inner class SpecialCharacterEscaping {
        @Test
        fun `should escape quotes in description`() {
            val schema = generator.generateSchema(SpecialCharsCommand())

            assertThat(schema).contains("\\\"quotes\\\"")
        }

        @Test
        fun `should escape newlines in description`() {
            val schema = generator.generateSchema(SpecialCharsCommand())

            assertThat(schema).contains("\\n")
        }
    }

    @Nested
    inner class MultipleOptions {
        @Test
        fun `should generate schema with multiple options`() {
            val schema = generator.generateSchema(MultipleOptionsCommand())

            assertThat(schema).contains(""""name":""")
            assertThat(schema).contains(""""count":""")
            assertThat(schema).contains(""""enabled":""")
        }

        @Test
        fun `should be valid JSON structure`() {
            val schema = generator.generateSchema(MultipleOptionsCommand())

            assertThat(schema).startsWith("{")
            assertThat(schema).endsWith("}")
            assertThat(schema).contains(""""type":"object"""")
            assertThat(schema).contains(""""properties":""")
        }
    }
}
