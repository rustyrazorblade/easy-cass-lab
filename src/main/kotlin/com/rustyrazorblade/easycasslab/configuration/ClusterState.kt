package com.rustyrazorblade.easycasslab.configuration

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.io.File

/**
 * Tracking state across multiple commands
 */
const val CLUSTER_STATE = "state.json"

data class NodeState(
    var version: String = "",
    var javaVersion: String = "",
)

data class AWS(
    var vpcId: String = "",
)

/**
 * Configuration from Init command to preserve cluster setup parameters
 * All fields have defaults to ensure backward compatibility with older state files
 */
data class InitConfig(
    val cassandraInstances: Int = 3,
    val stressInstances: Int = 0,
    val instanceType: String = "r3.2xlarge",
    val stressInstanceType: String = "c7i.2xlarge",
    val azs: List<String> = listOf(),
    val ami: String = "",
    val region: String = "us-west-2",
    val name: String = "cluster",
    val ebsType: String = "NONE",
    val ebsSize: Int = 256,
    val ebsIops: Int = 3000,
    val ebsThroughput: Int = 125,
    val ebsOptimized: Boolean = false,
    val open: Boolean = false,
    val controlInstances: Int = 1,
    val controlInstanceType: String = "t3.xlarge",
    val tags: Map<String, String> = mapOf(),
)

data class ClusterState(
    var name: String,
    // if we fire up a new node and just tell it to go, it should use all the defaults
    var default: NodeState = NodeState(),
    // we also have a per-node mapping that lets us override, per node
    var nodes: MutableMap<Alias, NodeState> = mutableMapOf(),
    var versions: MutableMap<String, String>?,
    // Configuration from Init command
    var initConfig: InitConfig? = null,
) {
    companion object {
        @JsonIgnore
        private val mapper = ObjectMapper().registerKotlinModule().apply {
            // Configure to handle missing fields and unknown properties gracefully
            configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false)
            configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES, false)
        }

        @JsonIgnore
        var fp = File(CLUSTER_STATE)

        fun load(): ClusterState = mapper.readValue(fp, ClusterState::class.java)
    }

    fun save() = mapper.writerWithDefaultPrettyPrinter().writeValue(fp, this)
}
