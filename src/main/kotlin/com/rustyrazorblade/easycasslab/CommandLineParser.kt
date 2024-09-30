package com.rustyrazorblade.easycasslab

import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter
import com.rustyrazorblade.easycasslab.commands.*
import org.apache.logging.log4j.kotlin.logger
import java.util.regex.Pattern

data class Command(val name: String, val command: ICommand, val aliases: List<String> = listOf())

class MainArgs {
    @Parameter(names = ["--help", "-h"], description = "Shows this help.")
    var help = false
}

class CommandLineParser(val context: Context) {
    val commands: List<Command>

    private val logger = logger("com.rustyrazorblade.easycasslab.CommandLineParser")
    private val jc: JCommander
    private val regex = """("([^"\\]|\\.)*"|'([^'\\]|\\.)*'|[^\s"']+)+""".toRegex()


    init {

        val jcommander = JCommander.newBuilder().programName("easy-cass-lab")
        val args = MainArgs()
        jcommander.addObject(args)

        commands = listOf(
            Command("build-base", BuildBaseImage(context)),
            Command("build-cassandra", BuildCassandraImage(context)),
            Command("build-image", BuildImage(context)),
            Command("clean", Clean()),
            Command("down", Down(context)),
            Command("download-config", DownloadConfig(context), listOf("dc")),
            Command("hosts", Hosts(context)),
            Command("init", Init(context)),
            Command("list", ListVersions(context), listOf("ls")),
            Command("setup-instances", SetupInstance(context), listOf("si")),
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
            Command("temporal-worker", RunTemporalWorker())
        )

        for(c in commands) {
            logger.debug { "Adding command: ${c.name}" }
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
        jc.parse(*input)
        commands.filter { it.name == jc.parsedCommand }.firstOrNull()?.run {
            this.command.execute()
        } ?: run {
            jc.usage()
        }
    }


}