package com.rustyrazorblade.easycasslab

enum class Containers(
    val containerName: String,
    val tag: String,
) {
    PACKER("hashicorp/packer", "full"),
    ;

    val imageWithTag: String
        get() = "$containerName:$tag"
}
