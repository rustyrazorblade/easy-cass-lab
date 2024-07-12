package com.rustyrazorblade.easycasslab.commands.delegates

import com.beust.jcommander.Parameter

class ReleaseFlag {
    @Parameter(description = "Release flag", names = ["--release"])
    var release: Boolean = false
}