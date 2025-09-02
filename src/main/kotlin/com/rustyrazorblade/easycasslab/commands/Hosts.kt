package com.rustyrazorblade.easycasslab.commands

import com.beust.jcommander.Parameter
import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.annotations.McpCommand
import com.rustyrazorblade.easycasslab.configuration.HostList
import com.rustyrazorblade.easycasslab.configuration.ServerType
import com.rustyrazorblade.easycasslab.output.OutputHandler
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.FileNotFoundException

@McpCommand
class Hosts(val context: Context) : ICommand, KoinComponent {
    private val outputHandler: OutputHandler by inject()

    @Parameter(names = ["-c"], description = "Show Cassandra as a comma delimited list")
    var cassandra: Boolean = false

    data class HostOutput(val cassandra: HostList, val stress: HostList, val control: HostList)

    override fun execute() {
        try {
            val output =
                with(context.tfstate) {
                    HostOutput(
                        getHosts(ServerType.Cassandra),
                        getHosts(ServerType.Stress),
                        getHosts(ServerType.Control),
                    )
                }

            if (cassandra) {
                val hosts = context.tfstate.getHosts(ServerType.Cassandra)
                val csv = hosts.map { it.public }.joinToString(",")
                outputHandler.handleMessage(csv)
            } else {
                context.yaml.writeValue(System.out, output)
            }
        } catch (ignored: FileNotFoundException) {
            outputHandler.handleMessage(
                "terraform.tfstate does not exist yet, most likely easy-cass-lab up has not been run.",
            )
        }
    }
}
