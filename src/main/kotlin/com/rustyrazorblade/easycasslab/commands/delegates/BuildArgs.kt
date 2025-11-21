@file:Suppress("EnumNaming")

package com.rustyrazorblade.easycasslab.commands.delegates

import com.beust.jcommander.Parameter

enum class Arch(
    val type: String,
) {
    AMD64("amd64"),
    ARM64("arm64"),
}

class BuildArgs {
    @Parameter(description = "Release flag", names = ["--release"])
    var release: Boolean = false

    @Parameter(description = "AWS region to build the image in", names = ["--region", "-r"])
    var region: String = ""

    @Parameter(description = "CPU architecture", names = ["--arch", "-a", "--cpu"])
    var arch: Arch = Arch.AMD64
}
