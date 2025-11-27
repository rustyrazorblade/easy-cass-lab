package com.rustyrazorblade.easycasslab

import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter
import com.fasterxml.jackson.annotation.JsonIgnore
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
import com.rustyrazorblade.easycasslab.commands.ICommand
import com.rustyrazorblade.easycasslab.commands.Init
import com.rustyrazorblade.easycasslab.commands.Ip
import com.rustyrazorblade.easycasslab.commands.ListVersions
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
import java.io.File
import kotlin.system.exitProcess

data class Command(
    val name: String,
    val command: ICommand,
    val aliases: List<String> = listOf(),
)

class MainArgs {
    @Parameter(names = ["--help", "-h"], description = "Shows this help.")
    var help = false
}

class CommandLineParser(
    val context: Context,
) : KoinComponent {
    private val userConfig: User by inject()
    val commands: List<Command>

    @JsonIgnore private val logger = KotlinLogging.logger {}
    private val jc: JCommander
    private val regex = """("([^"\\]|\\.)*"|'([^'\\]|\\.)*'|[^\s"']+)+""".toRegex()
    private val dockerClientProvider: DockerClientProvider by inject()
    private val outputHandler: OutputHandler by inject()

    init {

        val jcommander = JCommander.newBuilder().programName("easy-cass-lab")
        val args = MainArgs()
        jcommander.addObject(args)

        commands =
            listOf(
                Command("build-base", BuildBaseImage(context)),
                Command("build-cassandra", BuildCassandraImage(context)),
                Command("build-image", BuildImage(context)),
                Command("clean", Clean(context)),
                Command("down", Down(context)),
                Command("download-config", DownloadConfig(context), listOf("dc")),
                Command("exec", Exec(context)),
                Command("hosts", Hosts(context)),
                Command("init", Init(context)),
                Command("ip", Ip(context)),
                Command("list", ListVersions(context), listOf("ls")),
                Command("setup-instances", SetupInstance(context), listOf("si")),
                Command("setup-profile", SetupProfile(context), listOf("setup")),
                Command("start", Start(context)),
                Command("stop", Stop(context)),
                Command("restart", Restart(context)),
                Command("up", Up(context)),
                Command("update-config", UpdateConfig(context), listOf("uc")),
                Command("use", UseCassandra(context)),
                Command("write-config", WriteConfig(context), listOf("wc")),
                Command("configure-axonops", ConfigureAxonOps(context)),
                Command("upload-keys", UploadAuthorizedKeys(context)),
                Command("repl", Repl(context)),
                Command("version", Version(context)),
                Command("server", Server(context)),
                Command("prune-amis", PruneAMIs(context)),
                Command("show-iam-policies", ShowIamPolicies(context), listOf("sip")),
                Command("configure-aws", ConfigureAWS(context)),
                Command("spark-submit", SparkSubmit(context), listOf("ssj")),
            )

        for (c in commands) {
            logger.debug { "Adding command: ${c.name}" }
            @Suppress("SpreadOperator") // Required for varargs, used during initialization only
            jcommander.addCommand(c.name, c.command, *c.aliases.toTypedArray())
        }

        jc = jcommander.build()
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

    fun eval(input: Array<String>) {
        @Suppress("SpreadOperator") // Required for varargs
        jc.parse(*input)
        commands.firstOrNull { it.name == jc.parsedCommand }?.run {
            // Check if the command requires profile setup
            if (this.command::class.annotations.any { it is RequireProfileSetup }) {
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
            if (this.command::class.annotations.any { it is RequireDocker }) {
                if (!checkDockerAvailability()) {
                    outputHandler.handleError("Error: Docker is not available or not running.")
                    outputHandler.handleError(
                        "Please ensure Docker is installed and running before executing this command.",
                    )
                    exitProcess(1)
                }
            }
            // Check if the command requires an SSH key
            if (this.command::class.annotations.any { it is RequireSSHKey }) {
                if (!checkSSHKeyAvailability()) {
                    outputHandler.handleError("SSH key not found at ${userConfig.sshKeyPath}")
                    exitProcess(1)
                }
            }
            this.command.executeAll()
        }
            ?: run {
                jc.usage()
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
