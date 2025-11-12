package com.rustyrazorblade.easycasslab.commands

import com.beust.jcommander.Parameters
import com.beust.jcommander.ParametersDelegate
import com.github.ajalt.mordant.TermColors
import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.annotations.McpCommand
import com.rustyrazorblade.easycasslab.commands.delegates.Hosts
import com.rustyrazorblade.easycasslab.configuration.ServerType
import com.rustyrazorblade.easycasslab.services.CassandraService
import org.koin.core.component.inject

@McpCommand
@Parameters(commandDescription = "Restart cassandra", commandNames = ["restart"])
class Restart(
    context: Context,
) : BaseCommand(context) {
    private val cassandraService: CassandraService by inject()

    @ParametersDelegate var hosts = Hosts()

    override fun execute() {
        outputHandler.handleMessage("Restarting cassandra service on all nodes.")
        with(TermColors()) {
            tfstate.withHosts(ServerType.Cassandra, hosts) {
                cassandraService.restart(it).getOrThrow()
            }
        }
    }
}
