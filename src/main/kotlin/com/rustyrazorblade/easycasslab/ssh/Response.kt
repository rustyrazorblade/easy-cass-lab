package com.rustyrazorblade.easycasslab.ssh

/**
 * Represents a response from a remote command execution
 * @property text The standard output from the command
 * @property stderr The standard error output from the command
 */
data class Response(
    val text: String,
    val stderr: String = "",
)
