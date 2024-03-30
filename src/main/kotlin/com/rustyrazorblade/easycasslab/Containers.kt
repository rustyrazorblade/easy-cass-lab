package com.rustyrazorblade.easycasslab

enum class Containers(val containerName: String, val tag: String) {
    TERRAFORM("hashicorp/terraform", "1.7");

    val imageWithTag : String
        get() = "$containerName:$tag"
}