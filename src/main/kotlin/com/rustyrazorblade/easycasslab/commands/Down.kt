package com.rustyrazorblade.easycasslab.commands

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.annotations.RequireDocker
import com.rustyrazorblade.easycasslab.containers.Terraform
import com.rustyrazorblade.easycasslab.output.OutputHandler
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@RequireDocker
@Parameters(commandDescription = "Shut down a cluster")
class Down(val context: Context) : ICommand, KoinComponent {
    @Parameter(description = "Auto approve changes", names = ["--auto-approve", "-a", "--yes"])
    var autoApprove = false

    private val outputHandler: OutputHandler by inject()

    override fun execute() {
        outputHandler.handleMessage("Crushing dreams, terminating instances.")

        Terraform(context).down(autoApprove)
    }
}
