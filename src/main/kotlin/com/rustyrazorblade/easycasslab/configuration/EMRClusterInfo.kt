package com.rustyrazorblade.easycasslab.configuration

/**
 * Information about an EMR cluster for Spark job operations.
 *
 * @property clusterId The unique EMR cluster ID (e.g., "j-ABC123XYZ")
 * @property name The cluster name
 * @property masterPublicDns The public DNS name of the master node
 * @property state The current state of the cluster
 */
data class EMRClusterInfo(
    val clusterId: String,
    val name: String,
    val masterPublicDns: String?,
    val state: String,
)
