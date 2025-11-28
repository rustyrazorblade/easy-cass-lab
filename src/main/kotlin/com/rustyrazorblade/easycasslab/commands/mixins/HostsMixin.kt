package com.rustyrazorblade.easycasslab.commands.mixins

import picocli.CommandLine.Option

/**
 * PicoCLI mixin for host filtering parameters.
 *
 * Provides a reusable --hosts option that can be mixed into any command
 * that needs to target specific hosts. When empty, operations apply to all hosts.
 */
class HostsMixin {
    @Option(
        names = ["--hosts"],
        description = ["Hosts to run this on, leave blank for all hosts."],
    )
    var hostList: String = ""
}
