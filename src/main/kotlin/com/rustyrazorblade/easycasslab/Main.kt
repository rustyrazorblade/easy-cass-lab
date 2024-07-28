package com.rustyrazorblade.easycasslab

import org.apache.logging.log4j.kotlin.logger
import java.io.File


fun main(arguments: Array<String>) {

    val easycasslabUserDirectory = File(System.getProperty("user.home"), "/.easy-cass-lab/")

    val logger = logger(" com.rustyrazorblade.easycasslab.MainKt")

    val context = Context(easycasslabUserDirectory)
    val parser = CommandLineParser(context)
    parser.eval(arguments)

}

