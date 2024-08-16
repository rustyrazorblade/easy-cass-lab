package com.rustyrazorblade.easycasslab.commands.delegates

import com.beust.jcommander.Parameter

class Hosts {
    @Parameter(description = "Hosts to run this on, leave blank for all hosts.", names = ["--hosts"])
    var hosts = ""
    companion object {
        fun all() = Hosts()
    }
}