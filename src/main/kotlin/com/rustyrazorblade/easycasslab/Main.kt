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

    val commands = mapOf("init" to Init(context),
                         "up" to Up(context),
                         "start" to Start(context),
                         "stop" to Stop(context),
                         "down" to Down(context),
                         "build" to BuildCassandra(context),
                         "ls" to ListCassandraBuilds(context),
                         "use" to UseCassandra(context),
                         "clean" to Clean(),
                         "hosts" to Hosts(context))

    for(c in commands.entries) {
        logger.debug { "Adding command: ${c.key}" }
        jcommander.addCommand(c.key, c.value)
    }

    val jc = jcommander.build()
    jc.parse(*arguments)

    val commandObj = commands[jc.parsedCommand]

    if(commandObj != null)
        commandObj.execute()
    else
        jc.usage()
}

