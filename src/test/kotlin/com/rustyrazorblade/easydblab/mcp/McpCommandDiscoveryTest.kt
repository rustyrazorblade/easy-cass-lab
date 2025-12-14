package com.rustyrazorblade.easydblab.mcp

import com.rustyrazorblade.easydblab.BaseKoinTest
import com.rustyrazorblade.easydblab.annotations.McpCommand
import com.rustyrazorblade.easydblab.commands.PicoCommand
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import picocli.CommandLine.Command

/**
 * Tests for McpCommandDiscovery which scans for @McpCommand annotated classes
 * and derives tool names from package paths.
 */
class McpCommandDiscoveryTest : BaseKoinTest() {
    @Nested
    inner class ToolNameDerivation {
        @Test
        fun `should return command name for top-level package`() {
            // Simulate a command in the base commands package
            val toolName = McpCommandDiscovery.deriveMcpToolName(TopLevelTestCommand::class.java)

            // Since TopLevelTestCommand is in the mcp package (not commands), it would have a prefix
            // This test validates the derivation logic itself
            assertThat(toolName).isEqualTo("test-top-level")
        }

        @Test
        fun `should prefix with single package segment for nested command`() {
            // Test with actual nested commands from the codebase
            val sparkSubmitClass =
                Class.forName(
                    "com.rustyrazorblade.easydblab.commands.spark.SparkSubmit",
                )
            val toolName = McpCommandDiscovery.deriveMcpToolName(sparkSubmitClass)

            assertThat(toolName).isEqualTo("spark_submit")
        }

        @Test
        fun `should prefix with multiple package segments for deeply nested command`() {
            // Test with cassandra.stress commands
            val stressStartClass =
                Class.forName(
                    "com.rustyrazorblade.easydblab.commands.cassandra.stress.StressStart",
                )
            val toolName = McpCommandDiscovery.deriveMcpToolName(stressStartClass)

            assertThat(toolName).isEqualTo("cassandra_stress_start")
        }

        @Test
        fun `should handle cassandra package commands`() {
            val cassandraUpClass =
                Class.forName(
                    "com.rustyrazorblade.easydblab.commands.cassandra.Up",
                )
            val toolName = McpCommandDiscovery.deriveMcpToolName(cassandraUpClass)

            assertThat(toolName).isEqualTo("cassandra_up")
        }

        @Test
        fun `should handle clickhouse package commands`() {
            val clickHouseStartClass =
                Class.forName(
                    "com.rustyrazorblade.easydblab.commands.clickhouse.ClickHouseStart",
                )
            val toolName = McpCommandDiscovery.deriveMcpToolName(clickHouseStartClass)

            assertThat(toolName).isEqualTo("clickhouse_start")
        }

        @Test
        fun `should handle opensearch package commands`() {
            val openSearchStartClass =
                Class.forName(
                    "com.rustyrazorblade.easydblab.commands.opensearch.OpenSearchStart",
                )
            val toolName = McpCommandDiscovery.deriveMcpToolName(openSearchStartClass)

            assertThat(toolName).isEqualTo("opensearch_start")
        }

        @Test
        fun `should handle k8 package commands`() {
            val k8ApplyClass =
                Class.forName(
                    "com.rustyrazorblade.easydblab.commands.k8.K8Apply",
                )
            val toolName = McpCommandDiscovery.deriveMcpToolName(k8ApplyClass)

            assertThat(toolName).isEqualTo("k8_apply")
        }

        @Test
        fun `should keep top-level command name unchanged`() {
            val initClass =
                Class.forName(
                    "com.rustyrazorblade.easydblab.commands.Init",
                )
            val toolName = McpCommandDiscovery.deriveMcpToolName(initClass)

            assertThat(toolName).isEqualTo("init")
        }

        @Test
        fun `should keep clean command name unchanged`() {
            val cleanClass =
                Class.forName(
                    "com.rustyrazorblade.easydblab.commands.Clean",
                )
            val toolName = McpCommandDiscovery.deriveMcpToolName(cleanClass)

            assertThat(toolName).isEqualTo("clean")
        }
    }

    @Nested
    inner class CommandDiscovery {
        @Test
        fun `should discover all McpCommand annotated classes`() {
            val commands = McpCommandDiscovery.discoverMcpCommands(context)

            assertThat(commands).isNotEmpty
            // Should find top-level commands
            assertThat(commands.map { it.toolName }).contains("init", "clean", "status")
        }

        @Test
        fun `should discover nested spark commands`() {
            val commands = McpCommandDiscovery.discoverMcpCommands(context)

            assertThat(commands.map { it.toolName }).contains(
                "spark_submit",
                "spark_status",
                "spark_jobs",
                "spark_logs",
            )
        }

        @Test
        fun `should discover nested cassandra stress commands`() {
            val commands = McpCommandDiscovery.discoverMcpCommands(context)

            assertThat(commands.map { it.toolName }).contains(
                "cassandra_stress_start",
                "cassandra_stress_stop",
                "cassandra_stress_status",
                "cassandra_stress_logs",
                "cassandra_stress_list",
                "cassandra_stress_fields",
                "cassandra_stress_info",
            )
        }

        @Test
        fun `should discover nested clickhouse commands`() {
            val commands = McpCommandDiscovery.discoverMcpCommands(context)

            assertThat(commands.map { it.toolName }).contains(
                "clickhouse_start",
                "clickhouse_status",
                "clickhouse_stop",
            )
        }

        @Test
        fun `should discover nested opensearch commands`() {
            val commands = McpCommandDiscovery.discoverMcpCommands(context)

            assertThat(commands.map { it.toolName }).contains(
                "opensearch_start",
                "opensearch_status",
                "opensearch_stop",
            )
        }

        @Test
        fun `should discover k8 commands`() {
            val commands = McpCommandDiscovery.discoverMcpCommands(context)

            assertThat(commands.map { it.toolName }).contains("k8_apply")
        }

        @Test
        fun `discovered commands should have working factories`() {
            val commands = McpCommandDiscovery.discoverMcpCommands(context)

            commands.forEach { entry ->
                val command = entry.factory()
                assertThat(command).isInstanceOf(PicoCommand::class.java)
            }
        }

        @Test
        fun `should not include duplicate tool names`() {
            val commands = McpCommandDiscovery.discoverMcpCommands(context)
            val toolNames = commands.map { it.toolName }

            assertThat(toolNames).doesNotHaveDuplicates()
        }
    }

    // Test command in mcp package (not in commands package)
    @Command(name = "test-top-level")
    @McpCommand
    class TopLevelTestCommand : PicoCommand {
        override fun execute() {
            // Test implementation
        }
    }
}
