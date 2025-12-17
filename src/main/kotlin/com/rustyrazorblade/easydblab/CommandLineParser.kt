package com.rustyrazorblade.easydblab

import com.github.ajalt.mordant.TermColors
import com.rustyrazorblade.easydblab.commands.BuildBaseImage
import com.rustyrazorblade.easydblab.commands.BuildCassandraImage
import com.rustyrazorblade.easydblab.commands.BuildImage
import com.rustyrazorblade.easydblab.commands.Clean
import com.rustyrazorblade.easydblab.commands.ConfigureAWS
import com.rustyrazorblade.easydblab.commands.ConfigureAxonOps
import com.rustyrazorblade.easydblab.commands.Exec
import com.rustyrazorblade.easydblab.commands.Hosts
import com.rustyrazorblade.easydblab.commands.Init
import com.rustyrazorblade.easydblab.commands.Ip
import com.rustyrazorblade.easydblab.commands.PicoCommand
import com.rustyrazorblade.easydblab.commands.PruneAMIs
import com.rustyrazorblade.easydblab.commands.Repl
import com.rustyrazorblade.easydblab.commands.Server
import com.rustyrazorblade.easydblab.commands.SetupInstance
import com.rustyrazorblade.easydblab.commands.SetupProfile
import com.rustyrazorblade.easydblab.commands.ShowIamPolicies
import com.rustyrazorblade.easydblab.commands.Status
import com.rustyrazorblade.easydblab.commands.Up
import com.rustyrazorblade.easydblab.commands.UploadAuthorizedKeys
import com.rustyrazorblade.easydblab.commands.Version
import com.rustyrazorblade.easydblab.commands.aws.Aws
import com.rustyrazorblade.easydblab.commands.cassandra.Cassandra
import com.rustyrazorblade.easydblab.commands.clickhouse.ClickHouse
import com.rustyrazorblade.easydblab.commands.k8.K8
import com.rustyrazorblade.easydblab.commands.opensearch.OpenSearch
import com.rustyrazorblade.easydblab.commands.spark.Spark
import com.rustyrazorblade.easydblab.configuration.UserConfigProvider
import com.rustyrazorblade.easydblab.di.KoinCommandFactory
import com.rustyrazorblade.easydblab.output.OutputHandler
import com.rustyrazorblade.easydblab.services.BackupRestoreService
import com.rustyrazorblade.easydblab.services.CommandExecutor
import com.rustyrazorblade.easydblab.services.DefaultCommandExecutor
import io.github.oshai.kotlinlogging.KotlinLogging
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.component.inject
import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Model.CommandSpec
import picocli.CommandLine.Option
import picocli.CommandLine.Spec
import kotlin.system.exitProcess

/**
 * Root command for easy-db-lab CLI.
 * Declaratively registers all top-level commands and parent command groups.
 */
@Command(
    name = "easy-db-lab",
    description = ["Tool to create Cassandra lab environments in AWS"],
    mixinStandardHelpOptions = true,
    subcommands = [
        // Top-level commands
        Version::class,
        Clean::class,
        Ip::class,
        Hosts::class,
        Status::class,
        Exec::class,
        ConfigureAxonOps::class,
        UploadAuthorizedKeys::class,
        ShowIamPolicies::class,
        ConfigureAWS::class,
        PruneAMIs::class,
        BuildBaseImage::class,
        BuildCassandraImage::class,
        BuildImage::class,
        Init::class,
        SetupInstance::class,
        Up::class,
        SetupProfile::class,
        Repl::class,
        Server::class,
        // Parent command groups
        Spark::class,
        K8::class,
        ClickHouse::class,
        Cassandra::class,
        OpenSearch::class,
        Aws::class,
    ],
)
class EasyDBLabCommand : Runnable {
    @Spec
    lateinit var spec: CommandSpec

    @Option(
        names = ["--vpc-id"],
        description = ["Reconstruct state from existing VPC. Requires ClusterId tag on VPC."],
    )
    var vpcId: String? = null

    @Option(
        names = ["--force"],
        description = ["Force state reconstruction even if state.json already exists"],
    )
    var force: Boolean = false

    override fun run() {
        // Show help when no subcommand is provided
        spec.commandLine().usage(System.out)
    }
}

/**
 * Command line parser using PicoCLI with Koin dependency injection.
 *
 * All commands are registered declaratively via @Command annotations.
 * KoinCommandFactory provides command instances with injected dependencies.
 */
class CommandLineParser : KoinComponent {
    private val context: Context by inject()
    private val logger = KotlinLogging.logger {}
    private val regex = """("([^"\\]|\\.)*"|'([^'\\]|\\.)*'|[^\s"']+)+""".toRegex()
    private val outputHandler: OutputHandler by inject()

    /** The main PicoCLI CommandLine instance with all subcommands registered. */
    private val commandLine: CommandLine =
        CommandLine(EasyDBLabCommand::class.java, KoinCommandFactory()).apply {
            // Set exception handler to ensure non-zero exit code on exceptions
            executionExceptionHandler =
                CommandLine.IExecutionExceptionHandler { ex, cmd, _ ->
                    cmd.err.println(ex.message)
                    ex.printStackTrace(cmd.err)
                    Constants.ExitCodes.ERROR
                }

            // Set execution strategy to delegate to CommandExecutor for full lifecycle
            executionStrategy =
                CommandLine.IExecutionStrategy { parseResult ->
                    // Get the root command to check for --vpc-id option
                    val rootCmd = parseResult.commandSpec().userObject()
                    if (rootCmd is EasyDBLabCommand && rootCmd.vpcId != null) {
                        // Reconstruct state from VPC before running any subcommand
                        handleVpcIdStateReconstruction(rootCmd.vpcId!!, rootCmd.force)
                    }

                    // Find the deepest subcommand (handles nested commands like "spark submit")
                    var currentParseResult = parseResult.subcommand()
                    while (currentParseResult?.subcommand() != null) {
                        currentParseResult = currentParseResult.subcommand()
                    }

                    // Execute PicoCommands through CommandExecutor for full lifecycle
                    // (requirements, execution, scheduled commands, backup)
                    if (currentParseResult != null) {
                        val cmd = currentParseResult.commandSpec().userObject()
                        if (cmd is PicoCommand) {
                            // Check profile setup BEFORE resolving CommandExecutor
                            // to avoid triggering AWS dependency resolution chain.
                            // SetupProfile is exempt since it creates the profile.
                            val isSetupCommand = cmd is SetupProfile
                            if (!isSetupCommand) {
                                val userConfigProvider = get<UserConfigProvider>()
                                if (!userConfigProvider.isSetup()) {
                                    val output = get<OutputHandler>()
                                    output.handleError(
                                        "Profile not configured.\n" +
                                            "Please run 'easy-db-lab setup-profile' to configure your environment.",
                                    )
                                    return@IExecutionStrategy Constants.ExitCodes.ERROR
                                }
                            }

                            // Get CommandExecutor lazily when a command actually runs
                            val executor = get<CommandExecutor>() as DefaultCommandExecutor
                            return@IExecutionStrategy executor.executeTopLevel(cmd)
                        }
                    }

                    // Fallback for non-PicoCommand (like root command help)
                    CommandLine.RunLast().execute(parseResult)
                }
        }

    /**
     * List of all command names for REPL tab completion.
     */
    val commandNames: Set<String>
        get() =
            buildSet {
                fun collectNames(spec: CommandSpec) {
                    add(spec.name())
                    spec.aliases().forEach { add(it) }
                    spec.subcommands().values.forEach { collectNames(it.commandSpec) }
                }
                commandLine.commandSpec
                    .subcommands()
                    .values
                    .forEach { collectNames(it.commandSpec) }
            }

    /**
     * Handles state reconstruction from a VPC ID.
     *
     * This is triggered when --vpc-id is provided on the command line.
     * It reconstructs the local state.json from AWS resources associated with the VPC,
     * and restores cluster configuration files (kubeconfig, k8s manifests, cassandra.patch.yaml) from S3.
     *
     * @param vpcId The VPC ID to reconstruct state from
     * @param force If true, overwrites existing state.json
     * @throws IllegalStateException if state.json exists and --force is not provided
     */
    private fun handleVpcIdStateReconstruction(
        vpcId: String,
        force: Boolean,
    ) {
        val backupRestoreService: BackupRestoreService by inject()

        backupRestoreService
            .restoreFromVpc(
                vpcId = vpcId,
                workingDirectory = context.workingDirectory.absolutePath,
                force = force,
            ).onFailure { error ->
                logger.error(error) { "Failed to restore from VPC: $vpcId" }
                throw error
            }
    }

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

                            Profile not configured. Please run 'easy-db-lab setup-profile' to configure your environment.

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
}
