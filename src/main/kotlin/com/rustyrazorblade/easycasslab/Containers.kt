package com.rustyrazorblade.easycasslab

enum class Containers(val containerName: String, val tag: String) {
    PSSH("rustyrazorblade/pssh", "1.0"),
    TERRAFORM("hashicorp/terraform", "1.6");

    val imageWithTag : String
        get() = "$containerName:$tag"
}