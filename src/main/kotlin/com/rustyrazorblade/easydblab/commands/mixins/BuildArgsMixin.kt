package com.rustyrazorblade.easydblab.commands.mixins

import com.rustyrazorblade.easydblab.configuration.Arch
import picocli.CommandLine.Option

/**
 * PicoCLI mixin for image build arguments.
 *
 * Provides reusable options for building AMI images including
 * release mode, region selection, and CPU architecture.
 */
class BuildArgsMixin {
    @Option(
        names = ["--release"],
        description = ["Release flag"],
    )
    var release: Boolean = false

    @Option(
        names = ["--region", "-r"],
        description = ["AWS region to build the image in"],
    )
    var region: String = ""

    @Option(
        names = ["--arch", "-a", "--cpu"],
        description = ["CPU architecture"],
    )
    var arch: Arch = Arch.AMD64
}
