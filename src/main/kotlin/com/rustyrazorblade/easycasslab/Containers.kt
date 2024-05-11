package com.rustyrazorblade.easycasslab

enum class Containers(val containerName: String, val tag: String) {
    TERRAFORM("ghcr.io/opentofu/opentofu", "1.7");

    val imageWithTag : String
        get() = "$containerName:$tag"
}