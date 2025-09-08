package com.rustyrazorblade.easycasslab.commands

import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.output.OutputHandler
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class Version : ICommand, KoinComponent {
    private val context: Context by inject()
    private val outputHandler: OutputHandler by inject()

    override fun execute() {
        outputHandler.handleMessage(context.version.toString())
    }
}
