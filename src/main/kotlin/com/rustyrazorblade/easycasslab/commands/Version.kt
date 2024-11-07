package com.rustyrazorblade.easycasslab.commands

import com.rustyrazorblade.easycasslab.Context

class Version(val context: Context) : ICommand {
    override fun execute() {
        println(context.version)
    }
}