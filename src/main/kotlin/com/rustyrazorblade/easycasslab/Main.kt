package com.rustyrazorblade.easycasslab

import com.github.ajalt.mordant.TermColors
import java.io.File

fun main(arguments: Array<String>) {
    val easycasslabUserDirectory = File(System.getProperty("user.home"), "/.easy-cass-lab/")

    val context = Context(easycasslabUserDirectory)
    val parser = CommandLineParser(context)
    try {
        parser.eval(arguments)
    } catch (e: DockerException) {
        e.printStackTrace()

        with(TermColors()) {
            println(red("There was an error connecting to docker.  Please check if it is running."))
        }
    } catch (e: java.rmi.RemoteException) {
        with(TermColors()) {
            println(red("There was an error executing the remote command.  Try rerunning it."))
        }
    } catch (e: Exception) {
        with(TermColors()) {
            println(red("An unknown exception has occurred."))
        }
        with(TermColors()) {
            println(
                red(
                    "Does this look like an error with easy-cass-lab?  If so, please file a bug report at https://github.com/rustyrazorblade/easy-cass-lab/ with the following information:",
                ),
            )
        }
        println(e.message)
        println(e.stackTraceToString())
    }
}
