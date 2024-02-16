package com.rustyrazorblade.easycasslab

enum class Containers(val containerName: String, val tag: String) {
    GRAFONNET("thelastpickle/grafonnet", "1.0"),
    PSSH("rustyrazorblade/pssh", "1.0"),
    CASSANDRA_BUILD("rustyrazorblade/cassandra-build", "1.0"),
    TERRAFORM("hashicorp/terraform", "1.6");

    val imageWithTag : String
        get() = "$containerName:$tag"
}