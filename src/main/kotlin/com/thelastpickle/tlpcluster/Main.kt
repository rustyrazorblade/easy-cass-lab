package com.thelastpickle.tlpcluster

import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter
import com.thelastpickle.tlpcluster.commands.*
import org.apache.logging.log4j.kotlin.logger
import java.io.File

class MainArgs {
    @Parameter(names = ["--help", "-h"], description = "Shows this help.")
    var help = false
}


fun main(arguments: Array<String>) {

    val tlpclusterUserDirectory = File(System.getProperty("user.home"), "/.tlp-cluster/")

    val logger = logger("com.thelastpickle.tlpcluster.MainKt")

    val context = Context(tlpclusterUserDirectory)

    val jcommander = JCommander.newBuilder().programName("tlp-cluster")

    val args = MainArgs()
    jcommander.addObject(args)

    val commands = mapOf("init" to Init(context),
                         "up" to Up(context),
                         "start" to Start(context),
                         "stop" to Stop(context),
                         "install" to Install(context),
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

