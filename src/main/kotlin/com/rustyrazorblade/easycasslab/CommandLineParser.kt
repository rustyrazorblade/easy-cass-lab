package com.rustyrazorblade.easycasslab

import com.github.ajalt.mordant.TermColors
import com.rustyrazorblade.easycasslab.annotations.RequireDocker
import com.rustyrazorblade.easycasslab.annotations.RequireProfileSetup
import com.rustyrazorblade.easycasslab.annotations.RequireSSHKey
import com.rustyrazorblade.easycasslab.commands.BuildBaseImage
import com.rustyrazorblade.easycasslab.commands.BuildCassandraImage
import com.rustyrazorblade.easycasslab.commands.BuildImage
import com.rustyrazorblade.easycasslab.commands.Clean
import com.rustyrazorblade.easycasslab.commands.ConfigureAWS
import com.rustyrazorblade.easycasslab.commands.ConfigureAxonOps
import com.rustyrazorblade.easycasslab.commands.Down
import com.rustyrazorblade.easycasslab.commands.DownloadConfig
import com.rustyrazorblade.easycasslab.commands.Exec
import com.rustyrazorblade.easycasslab.commands.Hosts
import com.rustyrazorblade.easycasslab.commands.Init
import com.rustyrazorblade.easycasslab.commands.Ip
import com.rustyrazorblade.easycasslab.commands.ListVersions
import com.rustyrazorblade.easycasslab.commands.PicoCommand
import com.rustyrazorblade.easycasslab.commands.PruneAMIs
import com.rustyrazorblade.easycasslab.commands.Repl
import com.rustyrazorblade.easycasslab.commands.Restart
import com.rustyrazorblade.easycasslab.commands.Server
import com.rustyrazorblade.easycasslab.commands.SetupInstance
import com.rustyrazorblade.easycasslab.commands.SetupProfile
import com.rustyrazorblade.easycasslab.commands.ShowIamPolicies
import com.rustyrazorblade.easycasslab.commands.SparkSubmit
import com.rustyrazorblade.easycasslab.commands.Start
import com.rustyrazorblade.easycasslab.commands.Stop
import com.rustyrazorblade.easycasslab.commands.Up
import com.rustyrazorblade.easycasslab.commands.UpdateConfig
import com.rustyrazorblade.easycasslab.commands.UploadAuthorizedKeys
import com.rustyrazorblade.easycasslab.commands.UseCassandra
import com.rustyrazorblade.easycasslab.commands.Version
import com.rustyrazorblade.easycasslab.commands.WriteConfig
import com.rustyrazorblade.easycasslab.configuration.User
import com.rustyrazorblade.easycasslab.configuration.UserConfigProvider
import com.rustyrazorblade.easycasslab.output.OutputHandler
import com.rustyrazorblade.easycasslab.providers.docker.DockerClientProvider
import io.github.oshai.kotlinlogging.KotlinLogging
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Model.CommandSpec
import picocli.CommandLine.Spec
import java.io.File
import kotlin.system.exitProcess

/**
 * Represents a PicoCLI command registration.
 * Uses a factory function to create new instances for each execution.
 */
data class PicoCommandEntry(
    val name: String,
    val factory: () -> PicoCommand,
    val aliases: List<String> = listOf(),
)

/**
 * Root command for easy-cass-lab CLI.
 * When no subcommand is provided, displays help.
 */
@Command(
    name = "easy-cass-lab",
    description = ["Tool to create Cassandra lab environments in AWS"],
    mixinStandardHelpOptions = true,
)
class EasyCassLabCommand : Runnable {
    @Spec
    lateinit var spec: CommandSpec

    override fun run() {
        // Show help when no subcommand is provided
        spec.commandLine().usage(System.out)
    }
}

class CommandLineParser(
    val context: Context,
) : KoinComponent {
    private val userConfig: User by inject()

    /** Registry for PicoCLI commands. */
    val picoCommands: List<PicoCommandEntry>

    /** Map of command names and aliases to their PicoCLI entries for fast lookup. */
    private val picoCommandMap: Map<String, PicoCommandEntry>

    /** The main PicoCLI CommandLine instance with all subcommands registered. */
    private val commandLine: CommandLine

    private val logger = KotlinLogging.logger {}
    private val regex = """("([^"\\]|\\.)*"|'([^'\\]|\\.)*'|[^\s"']+)+""".toRegex()
    private val dockerClientProvider: DockerClientProvider by inject()
    private val outputHandler: OutputHandler by inject()

    init {
        // PicoCLI commands - all commands are now fully migrated
        // Each entry uses a factory to create fresh instances for each execution
        picoCommands =
            listOf(
                PicoCommandEntry("version", { Version(context) }),
                PicoCommandEntry("clean", { Clean(context) }),
                PicoCommandEntry("list", { ListVersions(context) }, listOf("ls")),
                PicoCommandEntry("ip", { Ip(context) }),
                PicoCommandEntry("hosts", { Hosts(context) }),
                PicoCommandEntry("stop", { Stop(context) }),
                PicoCommandEntry("start", { Start(context) }),
                PicoCommandEntry("restart", { Restart(context) }),
                PicoCommandEntry("exec", { Exec(context) }),
                PicoCommandEntry("down", { Down(context) }),
                PicoCommandEntry("download-config", { DownloadConfig(context) }, listOf("dc")),
                PicoCommandEntry("write-config", { WriteConfig(context) }, listOf("wc")),
                PicoCommandEntry("update-config", { UpdateConfig(context) }, listOf("uc")),
                PicoCommandEntry("use", { UseCassandra(context) }),
                PicoCommandEntry("configure-axonops", { ConfigureAxonOps(context) }),
                PicoCommandEntry("upload-keys", { UploadAuthorizedKeys(context) }),
                PicoCommandEntry("show-iam-policies", { ShowIamPolicies(context) }, listOf("sip")),
                PicoCommandEntry("configure-aws", { ConfigureAWS(context) }),
                PicoCommandEntry("prune-amis", { PruneAMIs(context) }),
                PicoCommandEntry("spark-submit", { SparkSubmit(context) }, listOf("ssj")),
                PicoCommandEntry("build-base", { BuildBaseImage(context) }),
                PicoCommandEntry("build-cassandra", { BuildCassandraImage(context) }),
                PicoCommandEntry("build-image", { BuildImage(context) }),
                PicoCommandEntry("init", { Init(context) }),
                PicoCommandEntry("setup-instances", { SetupInstance(context) }, listOf("si")),
                PicoCommandEntry("up", { Up(context) }),
                PicoCommandEntry("setup-profile", { SetupProfile(context) }, listOf("setup")),
                PicoCommandEntry("repl", { Repl(context) }),
                PicoCommandEntry("server", { Server(context) }),
            )

        // Build lookup map including aliases
        picoCommandMap =
            buildMap {
                for (entry in picoCommands) {
                    put(entry.name, entry)
                    for (alias in entry.aliases) {
                        put(alias, entry)
                    }
                }
            }

        // Create root command and register all subcommands
        val rootCommand = EasyCassLabCommand()
        commandLine = CommandLine(rootCommand)

        // Register all subcommands with PicoCLI
        // Note: aliases should be defined in the @Command annotation on each command class
        for (entry in picoCommands) {
            val cmd = entry.factory()
            val subCommandLine = commandLine.addSubcommand(entry.name, cmd)
            // Register aliases from our PicoCommandEntry (for backward compatibility with REPL)
            for (alias in entry.aliases) {
                subCommandLine.commandSpec.aliases(*arrayOf(alias))
            }
        }

        // Set execution strategy to check requirements before running commands
        commandLine.executionStrategy =
            CommandLine.IExecutionStrategy { parseResult ->
                // Check requirements for the subcommand if present
                val subcommandParseResult = parseResult.subcommand()
                if (subcommandParseResult != null) {
                    val cmd = subcommandParseResult.commandSpec().userObject()
                    if (cmd is PicoCommand) {
                        checkCommandRequirements(cmd)
                    }
                }
                // Execute the command using default RunLast strategy
                CommandLine.RunLast().execute(parseResult)
            }
    }

    /**
     * Checks if a command name or alias refers to a registered command.
     */
    fun isPicoCommand(name: String): Boolean = picoCommandMap.containsKey(name)

    // For the repl
    fun eval(input: String) {
        val matches = regex.findAll(input)
        val result = mutableListOf<String>()

        for (match in matches) {
            result.add(match.value.trim('"', '\''))
        }

        return eval(result.toTypedArray())
    }

    @Suppress("SpreadOperator")
    fun eval(input: Array<String>) {
        // Use PicoCLI for all command execution
        val exitCode = commandLine.execute(*input)

        // Show profile setup hint if no command was provided and profile not configured
        if (input.isEmpty() || (input.size == 1 && (input[0] == "--help" || input[0] == "-h"))) {
            val userConfigProvider: UserConfigProvider by inject()
            if (!userConfigProvider.isSetup()) {
                with(TermColors()) {
                    outputHandler.handleMessage(
                        yellow(
                            """

                            Profile not configured. Please run 'easy-cass-lab setup-profile' to configure your environment.

                            """.trimIndent(),
                        ),
                    )
                }
            }
        }

        if (exitCode != 0) {
            exitProcess(exitCode)
        }
    }

    /**
     * Checks and enforces command requirements based on annotations.
     */
    private fun checkCommandRequirements(command: Any) {
        // Check if the command requires profile setup
        if (command::class.annotations.any { it is RequireProfileSetup }) {
            val userConfigProvider: UserConfigProvider by inject()
            if (!userConfigProvider.isSetup()) {
                // Run setup command
                val setupCommand = SetupProfile(context)
                setupCommand.execute()

                // Show message and exit
                with(TermColors()) {
                    outputHandler.handleMessage(green("\nYou can now run the command again."))
                }
                exitProcess(0)
            }
        }
        // Check if the command requires Docker
        if (command::class.annotations.any { it is RequireDocker }) {
            if (!checkDockerAvailability()) {
                outputHandler.handleError("Error: Docker is not available or not running.")
                outputHandler.handleError(
                    "Please ensure Docker is installed and running before executing this command.",
                )
                exitProcess(1)
            }
        }
        // Check if the command requires an SSH key
        if (command::class.annotations.any { it is RequireSSHKey }) {
            if (!checkSSHKeyAvailability()) {
                outputHandler.handleError("SSH key not found at ${userConfig.sshKeyPath}")
                exitProcess(1)
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun checkDockerAvailability(): Boolean =
        try {
            val dockerClient = dockerClientProvider.getDockerClient()
            // Try to list images as a simple health check
            dockerClient.listImages("", "")
            true
        } catch (e: Exception) {
            logger.error(e) { "Docker availability check failed" }
            false
        }

    private fun checkSSHKeyAvailability(): Boolean = File(userConfig.sshKeyPath).exists()
}
