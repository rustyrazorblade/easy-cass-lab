package com.rustyrazorblade.easycasslab.commands

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import com.beust.jcommander.converters.FileConverter
import  com.rustyrazorblade.easycasslab.Context
import  com.rustyrazorblade.easycasslab.containers.CassandraBuildJava8
import java.io.File
import java.lang.Exception

class CassandraDirectoryNotFound : Exception()

@Parameters(commandDescription = "Create a custom named Cassandra build from a working directory.")
class BuildCassandra(val context: Context)  : ICommand {

    @Parameter(description = "Name of build", names = ["-n"])
    lateinit var name : String

    @Parameter(description = "Path to build")
    lateinit var location : String

    override fun execute() {

        val dir = File(location)

        if(!dir.exists()) {
            throw CassandraDirectoryNotFound()
        }

        context.createBuildSkeleton(name)

        val cassandraBuilder = CassandraBuildJava8(context)

        // create the container
        println("Starting cassandra build process")
        
        cassandraBuilder.runBuild(dir.absolutePath, name)
    }
}