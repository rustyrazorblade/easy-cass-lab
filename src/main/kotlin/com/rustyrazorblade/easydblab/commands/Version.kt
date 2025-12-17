package com.rustyrazorblade.easydblab.commands

import com.rustyrazorblade.easydblab.Context
import com.rustyrazorblade.easydblab.output.OutputHandler
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import picocli.CommandLine.Command

/**
 * Displays the current version of easy-db-lab.
 */
@Command(
    name = "version",
    description = ["Display the easy-db-lab version"],
)
class Version :
    PicoCommand,
    KoinComponent {
    private val context: Context by inject()
    private val outputHandler: OutputHandler by inject()

    override fun execute() {
        outputHandler.handleMessage(context.version.toString())
    }
}
