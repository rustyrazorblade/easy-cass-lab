package com.rustyrazorblade.easycasslab.commands

import com.beust.jcommander.Parameters
import com.beust.jcommander.ParametersDelegate
import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.annotations.RequireSSHKey
import com.rustyrazorblade.easycasslab.commands.delegates.Hosts
import com.rustyrazorblade.easycasslab.configuration.ServerType
import com.rustyrazorblade.easycasslab.services.CassandraService
import org.koin.core.component.inject

// @McpCommand
@RequireSSHKey
@Parameters(commandDescription = "Stop cassandra on all nodes via service command")
class Stop(
    context: Context,
) : BaseCommand(context) {
    private val cassandraService: CassandraService by inject()

    @ParametersDelegate var hosts = Hosts()

    override fun execute() {
        outputHandler.handleMessage("Stopping cassandra service on all nodes.")

        tfstate.withHosts(ServerType.Cassandra, hosts) {
            cassandraService.stop(it).getOrThrow()
        }
    }
}
