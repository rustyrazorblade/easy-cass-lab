package com.rustyrazorblade.easycasslab.commands

import com.beust.jcommander.Parameters
import com.rustyrazorblade.easycasslab.Context
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

@Parameters(commandDescription = "Start server mode")
class Server(val context: Context) : ICommand {
    companion object {
        private const val DEFAULT_SERVER_PORT = 6100
    }

    override fun execute() {
        val port = DEFAULT_SERVER_PORT
        println("Starting Ktor server on port $port...")
        embeddedServer(Netty, port = port) {
            routing {
                get("/") {
                    call.respondText("Easy Cass Lab Server is running!")
                }
            }
        }.start(wait = true)
    }
}
