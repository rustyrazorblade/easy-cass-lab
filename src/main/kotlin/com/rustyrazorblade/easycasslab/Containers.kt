package com.rustyrazorblade.easycasslab

enum class Containers(val containerName: String, val tag: String) {
    TERRAFORM("ghcr.io/opentofu/opentofu", "1.10.7"),
    PACKER("hashicorp/packer", "full"),
    ;

    val imageWithTag: String
        get() = "$containerName:$tag"
}
