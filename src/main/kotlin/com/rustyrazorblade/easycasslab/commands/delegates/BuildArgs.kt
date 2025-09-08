@file:Suppress("EnumNaming")

package com.rustyrazorblade.easycasslab.commands.delegates

import com.beust.jcommander.Parameter
import com.rustyrazorblade.easycasslab.Context
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

enum class Arch(val type: String) {
    amd64("amd64"),
    arm64("arm64"),
}

class BuildArgs : KoinComponent {
    private val context: Context by inject()
    
    @Parameter(description = "Release flag", names = ["--release"])
    var release: Boolean = false

    @Parameter(description = "AWS region to build the image in", names = ["--region", "-r"])
    var region = context.userConfig.region

    @Parameter(description = "CPU architecture", names = ["--arch", "-a", "--cpu"])
    var arch = Arch.amd64
}
