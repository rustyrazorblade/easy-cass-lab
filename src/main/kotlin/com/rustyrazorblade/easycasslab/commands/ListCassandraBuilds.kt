package com.rustyrazorblade.easycasslab.commands

import com.beust.jcommander.Parameters
import  com.rustyrazorblade.easycasslab.Context
import java.io.File

@Parameters(commandDescription = "List available builds", commandNames = ["list", "ls"])
class ListCassandraBuilds(val context: Context) : ICommand {
    override fun execute() {
        val currentFile = File(context.cassandraRepo.buildDir, "CURRENT")
        val currentBuild = if (currentFile.exists()) File(context.cassandraRepo.buildDir, "CURRENT").readText() else ""
        val files = context.cassandraRepo.listBuilds()
        for(f in files) {
            if (f == currentBuild) {
                println(" *$f")
                continue
            }

            println("  $f")
        }
    }
}