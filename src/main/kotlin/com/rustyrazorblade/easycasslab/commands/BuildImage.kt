package com.rustyrazorblade.easycasslab.commands

import com.beust.jcommander.Parameters
import com.beust.jcommander.ParametersDelegate
import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.commands.delegates.ReleaseFlag
import com.rustyrazorblade.easycasslab.containers.Packer

@Parameters(commandDescription = "Build both the base and Cassandra image.")
class BuildImage(val context: Context) : ICommand {
    @ParametersDelegate
    var releaseFlag = ReleaseFlag()

    override fun execute() {
        BuildBaseImage(context)
            .apply { this.releaseFlag=this@BuildImage.releaseFlag }
            .execute()
        BuildCassandraImage(context)
            .apply { this.releaseFlag=this@BuildImage.releaseFlag }
            .execute()
    }
}
