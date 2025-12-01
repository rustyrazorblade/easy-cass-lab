package com.rustyrazorblade.easydblab.configuration

typealias Alias = String

/**
 * Represents a host in the cluster.
 *
 * @property public Public IP address of the host
 * @property private Private IP address of the host
 * @property alias Host alias (e.g., "cassandra0", "stress0", "control0")
 * @property availabilityZone AWS availability zone where the host is located
 */
data class Host(
    val public: String,
    val private: String,
    val alias: Alias,
    val availabilityZone: String,
)
