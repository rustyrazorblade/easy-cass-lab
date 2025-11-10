package com.rustyrazorblade.easycasslab.commands

import com.beust.jcommander.Parameters
import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.output.OutputHandler
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@Parameters(commandDescription = "Start server mode")
class Server(
    val context: Context,
) : ICommand,
    KoinComponent {
    private val outputHandler: OutputHandler by inject()

    companion object {
        private const val DEFAULT_SERVER_PORT = 6100
    }

    override fun execute() {
        val port = DEFAULT_SERVER_PORT
        outputHandler.handleMessage("Starting Ktor server on port $port...")
        embeddedServer(Netty, port = port) {
            routing { get("/") { call.respondText("Easy Cass Lab Server is running!") } }
        }.start(wait = true)
    }
}
