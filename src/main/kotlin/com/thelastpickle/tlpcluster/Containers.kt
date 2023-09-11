package com.thelastpickle.tlpcluster

enum class Containers(val containerName: String, val tag: String) {
    GRAFONNET("thelastpickle/grafonnet", "1.0"),
    PSSH("thelastpickle/pssh", "1.0"),
    CASSANDRA_BUILD("thelastpickle/cassandra-build", "1.0"),
    TERRAFORM("hashicorp/terraform", "1.5");

    val imageWithTag : String
        get() = "$containerName:$tag"
}