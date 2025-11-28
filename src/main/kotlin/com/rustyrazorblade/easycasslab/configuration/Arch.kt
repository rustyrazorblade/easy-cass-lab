package com.rustyrazorblade.easycasslab.configuration

/**
 * CPU architecture enum for AMI and container builds.
 *
 * @property type The architecture string used in packer and AWS APIs
 */
enum class Arch(
    val type: String,
) {
    AMD64("amd64"),
    ARM64("arm64"),
}
