package com.rustyrazorblade.easydblab.configuration

enum class ServerType(
    val serverType: String,
) {
    Cassandra("db"),
    Stress("stress"),
    Control("control"),
}
