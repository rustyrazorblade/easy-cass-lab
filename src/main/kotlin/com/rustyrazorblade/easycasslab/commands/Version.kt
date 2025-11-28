package com.rustyrazorblade.easycasslab.commands

import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.output.OutputHandler
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import picocli.CommandLine.Command

/**
 * Displays the current version of easy-cass-lab.
 */
@Command(
    name = "version",
    description = ["Display the easy-cass-lab version"],
)
class Version(
    private val context: Context,
) : PicoCommand,
    KoinComponent {
    private val outputHandler: OutputHandler by inject()

    override fun execute() {
        outputHandler.handleMessage(context.version.toString())
    }
}
