package com.rustyrazorblade.easydblab.configuration

enum class ServerType(
    val serverType: String,
) {
    Cassandra("cassandra"),
    Stress("stress"),
    Control("control"),
}
