package com.rustyrazorblade.easycasslab

import com.github.ajalt.mordant.TermColors
import com.github.dockerjava.api.exception.DockerException
import com.rustyrazorblade.easycasslab.di.KoinModules
import com.rustyrazorblade.easycasslab.di.contextModule
import io.github.oshai.kotlinlogging.KotlinLogging
import org.koin.core.context.startKoin
import java.io.File
import kotlin.system.exitProcess

private val log = KotlinLogging.logger {}

@Suppress("TooGenericExceptionCaught")
fun main(arguments: Array<String>) {
    val easycasslabUserDirectory = File(System.getProperty("user.home"), "/.easy-cass-lab/")

    // Create factory and get default context
    val contextFactory = ContextFactory(easycasslabUserDirectory)
    val defaultContext = contextFactory.getDefault()

    // Initialize Koin dependency injection with context-specific configuration
    startKoin {
        modules(
            KoinModules.getAllModules(defaultContext) + contextModule(contextFactory),
        )
    }
    val parser = CommandLineParser(defaultContext)
    try {
        parser.eval(arguments)
    } catch (e: DockerException) {
        log.error(e) { "Docker connection error" }
        with(TermColors()) {
            println(red("There was an error connecting to docker.  Please check if it is running."))
        }
        exitProcess(1)
    } catch (e: java.rmi.RemoteException) {
        log.error(e) { "Remote execution error" }
        with(TermColors()) {
            println(red("There was an error executing the remote command.  Try rerunning it."))
        }
        exitProcess(1)
    } catch (e: IllegalArgumentException) {
        log.error(e) { "Invalid argument provided" }
        with(TermColors()) { println(red("Invalid argument: ${e.message}")) }
        exitProcess(1)
    } catch (e: IllegalStateException) {
        log.error(e) { "Invalid state encountered" }
        with(TermColors()) { println(red("Invalid state: ${e.message}")) }
    } catch (e: RuntimeException) {
        log.error(e) { "A runtime exception has occurred" }
        with(TermColors()) {
            println(red("An unexpected error has occurred."))
            println(
                red(
                    "Does this look like an error with easy-cass-lab?  If so, please file a bug report at " +
                        "https://github.com/rustyrazorblade/easy-cass-lab/ with the following information:",
                ),
            )
        }
        println(e.message)
        println(e.stackTraceToString())
        exitProcess(1)
    }
}
