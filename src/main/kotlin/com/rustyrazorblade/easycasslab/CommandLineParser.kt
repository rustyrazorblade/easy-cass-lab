package com.rustyrazorblade.easycasslab

import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter
import com.fasterxml.jackson.annotation.JsonIgnore
import com.rustyrazorblade.easycasslab.annotations.RequireDocker
import com.rustyrazorblade.easycasslab.annotations.RequireSSHKey
import com.rustyrazorblade.easycasslab.configuration.User
import com.rustyrazorblade.easycasslab.commands.BuildBaseImage
import com.rustyrazorblade.easycasslab.commands.BuildCassandraImage
import com.rustyrazorblade.easycasslab.commands.BuildImage
import com.rustyrazorblade.easycasslab.commands.Clean
import com.rustyrazorblade.easycasslab.commands.ConfigureAxonOps
import com.rustyrazorblade.easycasslab.commands.Down
import com.rustyrazorblade.easycasslab.commands.DownloadConfig
import com.rustyrazorblade.easycasslab.commands.Hosts
import com.rustyrazorblade.easycasslab.commands.ICommand
import com.rustyrazorblade.easycasslab.commands.Init
import com.rustyrazorblade.easycasslab.commands.ListVersions
import com.rustyrazorblade.easycasslab.commands.McpCommand
import com.rustyrazorblade.easycasslab.commands.McpConfigCommand
import com.rustyrazorblade.easycasslab.commands.Repl
import com.rustyrazorblade.easycasslab.commands.Restart
import com.rustyrazorblade.easycasslab.commands.Server
import com.rustyrazorblade.easycasslab.commands.SetupInstance
import com.rustyrazorblade.easycasslab.commands.Start
import com.rustyrazorblade.easycasslab.commands.Stop
import com.rustyrazorblade.easycasslab.commands.Up
import com.rustyrazorblade.easycasslab.commands.UpdateConfig
import com.rustyrazorblade.easycasslab.commands.UploadAuthorizedKeys
import com.rustyrazorblade.easycasslab.commands.UseCassandra
import com.rustyrazorblade.easycasslab.commands.Version
import com.rustyrazorblade.easycasslab.commands.WriteConfig
import com.rustyrazorblade.easycasslab.output.OutputHandler
import com.rustyrazorblade.easycasslab.providers.docker.DockerClientProvider
import io.github.oshai.kotlinlogging.KotlinLogging
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import kotlin.system.exitProcess

data class Command(val name: String, val command: ICommand, val aliases: List<String> = listOf())

class MainArgs {
    @Parameter(names = ["--help", "-h"], description = "Shows this help.")
    var help = false
}

class CommandLineParser : KoinComponent {
    val context: Context by inject()
    private val userConfig: User by inject()
    val commands: List<Command>

    @JsonIgnore
    private val logger = KotlinLogging.logger {}
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
                Command("build-base", BuildBaseImage()),
                Command("build-cassandra", BuildCassandraImage()),
                Command("build-image", BuildImage()),
                Command("clean", Clean()),
                Command("down", Down()),
                Command("download-config", DownloadConfig(), listOf("dc")),
                Command("hosts", Hosts()),
                Command("init", Init()),
                Command("list", ListVersions(), listOf("ls")),
                Command("setup-instances", SetupInstance(), listOf("si")),
                Command("start", Start()),
                Command("stop", Stop()),
                Command("restart", Restart()),
                Command("up", Up()),
                Command("update-config", UpdateConfig(), listOf("uc")),
                Command("use", UseCassandra()),
                Command("write-config", WriteConfig(), listOf("wc")),
                Command("configure-axonops", ConfigureAxonOps()),
                Command("upload-keys", UploadAuthorizedKeys()),
                Command("repl", Repl()),
                Command("server", Server()),
                Command("version", Version()),
                Command("mcp", McpCommand()),
                Command("mcp-config", McpConfigCommand()),
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
        commands.filter { it.name == jc.parsedCommand }.firstOrNull()?.run {
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
            this.command.execute()
        } ?: run {
            jc.usage()
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun checkDockerAvailability(): Boolean {
        return try {
            val dockerClient = dockerClientProvider.getDockerClient()
            // Try to list images as a simple health check
            dockerClient.listImages("", "")
            true
        } catch (e: Exception) {
            logger.error(e) { "Docker availability check failed" }
            false
        }
    }

    private fun checkSSHKeyAvailability(): Boolean {
        return File(userConfig.sshKeyPath).exists()
    }
}
