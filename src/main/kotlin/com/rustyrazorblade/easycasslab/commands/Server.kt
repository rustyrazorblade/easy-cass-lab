package com.rustyrazorblade.easycasslab.commands

import com.beust.jcommander.Parameters
import com.rustyrazorblade.easycasslab.Context
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

@Parameters(commandDescription = "Start server mode")
class Server(val context: Context) : ICommand {
    override fun execute() {
        val port = 6100
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
