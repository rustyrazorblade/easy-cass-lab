package com.rustyrazorblade.easycasslab.commands

import com.rustyrazorblade.easycasslab.temporal.Worker

// TODO (jwest): this may be temporary until we figure out how to run the worker as part of deployment
class RunTemporalWorker : ICommand {
    override fun execute() {
        Worker().run()
    }
}