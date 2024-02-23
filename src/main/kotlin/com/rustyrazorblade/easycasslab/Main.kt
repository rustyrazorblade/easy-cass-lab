package com.rustyrazorblade.easycasslab

import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter
import  com.rustyrazorblade.easycasslab.commands.*
import org.apache.logging.log4j.kotlin.logger
import java.io.File

class MainArgs {
    @Parameter(names = ["--help", "-h"], description = "Shows this help.")
    var help = false
}


fun main(arguments: Array<String>) {

    val easycasslabUserDirectory = File(System.getProperty("user.home"), "/.easy-cass-lab/")

    val logger = logger(" com.rustyrazorblade.easycasslab.MainKt")

    val context = Context(easycasslabUserDirectory)

    val jcommander = JCommander.newBuilder().programName("easy-cass-lab")

    val args = MainArgs()
    jcommander.addObject(args)

    data class Command(val name: String, val command: ICommand, val aliases: List<String> = listOf())

    val commands = listOf(
        Command("clean", Clean()),
        Command("down", Down(context)),
        Command("hosts", Hosts(context)),
        Command("init", Init(context)),
        Command("setup-disks", SetupDisks()),
        Command("start", Start(context)),
        Command("stop", Stop(context)),
        Command("up", Up(context)),
        Command("update-config", UpdateConfig(context), listOf("uc")),
        Command("use", UseCassandra(context)),
        Command("write-config", WriteConfig(context), listOf("wc")),
        Command("configure-axonops", ConfigureAxonOps(context))
    )

    for(c in commands) {
        logger.debug { "Adding command: ${c.name}" }
        jcommander.addCommand(c.name, c.command, *c.aliases.toTypedArray())
    }

    val jc = jcommander.build()
    jc.parse(*arguments)

    commands.filter { it.name == jc.parsedCommand }.firstOrNull()?.run {
        this.command.execute()
    } ?: run {
        jc.usage()
    }
}

