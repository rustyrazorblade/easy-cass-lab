package com.rustyrazorblade.easycasslab.commands

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import com.rustyrazorblade.easycasslab.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class McpServerConfig(
    val command: String,
    val args: List<String>,
    val env: Map<String, String> = emptyMap(),
)

@Serializable
data class McpConfig(
    val mcpServers: Map<String, McpServerConfig>,
)

/**
 * Command to output MCP server configuration for Claude Desktop. This command generates the
 * configuration needed to register easy-cass-lab as an MCP server in Claude Desktop's configuration
 * file.
 */
@Parameters(commandDescription = "Generate MCP server configuration for Claude Desktop")
class McpConfigCommand(context: Context) : BaseCommand(context) {
    @Parameter(
        names = ["--json"],
        description = "Output JSON configuration instead of instructions",
    )
    var json: Boolean = false

    @Parameter(
        names = ["--path"],
        description = "Path to easy-cass-lab installation (auto-detected if not specified)",
    )
    var installPath: String? = null

    override fun execute() {
        // Determine the installation path
        val easyCassLabPath = installPath ?: detectInstallationPath()

        if (easyCassLabPath == null) {
            println("Error: Could not detect easy-cass-lab installation path.")
            println("Please specify the path using --path parameter.")
            return
        }

        if (json) {
            outputJsonConfig(easyCassLabPath)
        } else {
            outputInstructions(easyCassLabPath)
        }
    }

    private fun detectInstallationPath(): String? {
        // First, try to find the location of the running script
        val currentDir = File(System.getProperty("user.dir"))

        // Check if we're running from the project directory
        val binDir = File(currentDir, "bin")
        val easyCassLabScript = File(binDir, "easy-cass-lab")

        if (easyCassLabScript.exists()) {
            return currentDir.absolutePath
        }

        // Check if we're running from a distribution
        val parentDir = currentDir.parentFile
        if (parentDir != null) {
            val parentBinDir = File(parentDir, "bin")
            val parentScript = File(parentBinDir, "easy-cass-lab")
            if (parentScript.exists()) {
                return parentDir.absolutePath
            }
        }

        // Try to find from the JAR location
        val jarPath = this::class.java.protectionDomain.codeSource?.location?.path
        if (jarPath != null) {
            val jarFile = File(jarPath)
            // If running from build/libs, go up to project root
            if (jarFile.path.contains("build/libs")) {
                val projectRoot = jarFile.parentFile?.parentFile?.parentFile
                if (projectRoot != null && File(projectRoot, "bin/easy-cass-lab").exists()) {
                    return projectRoot.absolutePath
                }
            }
        }

        return null
    }

    private fun outputJsonConfig(easyCassLabPath: String) {
        val config =
            McpConfig(
                mcpServers =
                    mapOf(
                        "easy-cass-lab" to
                            McpServerConfig(
                                command =
                                    "$easyCassLabPath/bin/easy-cass-lab",
                                args = listOf("mcp"),
                                env =
                                    mapOf(
                                        "PATH" to
                                            "\$PATH:$easyCassLabPath/bin",
                                    ),
                            ),
                    ),
            )

        val json =
            Json {
                prettyPrint = true
                encodeDefaults = true
            }
        println(json.encodeToString(config))
    }

    private fun outputInstructions(easyCassLabPath: String) {
        println(
            """
            ========================================
            MCP Server Configuration for Claude Desktop
            ========================================

            To register easy-cass-lab as an MCP server in Claude Desktop:

            1. Open Claude Desktop settings
            2. Navigate to the Developer section
            3. Edit the configuration file (claude_desktop_config.json)
            4. Add the following to the "mcpServers" section:

            {
              "mcpServers": {
                "easy-cass-lab": {
                  "command": "$easyCassLabPath/bin/easy-cass-lab",
                  "args": ["mcp"],
                  "env": {
                    "PATH": "${'$'}PATH:$easyCassLabPath/bin"
                  }
                }
              }
            }

            5. Restart Claude Desktop

            After configuration, easy-cass-lab commands will be available as MCP tools
            in Claude Desktop, allowing Claude to directly manage your Cassandra clusters.

            Available Commands:
            - up: Start Cassandra cluster instances
            - down: Shut down cluster instances
            - hosts: List cluster hosts
            - start/stop/restart: Control Cassandra services
            - And many more...

            Note: Make sure easy-cass-lab is properly installed and the path is correct.
            Current detected path: $easyCassLabPath

            To output just the JSON configuration, run:
            easy-cass-lab mcp-config --json
            """.trimIndent(),
        )
    }
}
