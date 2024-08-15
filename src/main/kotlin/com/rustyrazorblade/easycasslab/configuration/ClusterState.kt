package com.rustyrazorblade.easycasslab.configuration

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.rustyrazorblade.easycasslab.commands.Init
import java.io.File


/**
 * Tracking state across multiple commands
 */
const val CLUSTER_STATE = "state.json"

data class NodeState(
    var version: String = "",
    var javaVersion: String = ""
)

data class ClusterState(

    var name: String,
    // if we fire up a new node and just tell it to go, it should use all the defaults
    var default: NodeState = NodeState(),
    // we also have a per-node mapping that lets us override, per node
    var nodes: MutableMap<Alias, NodeState> = mutableMapOf(),

    var versions: MutableMap<String, String>?

) {

    companion object {
        @JsonIgnore
        private val mapper = ObjectMapper().registerKotlinModule()

        @JsonIgnore
        var fp = File(CLUSTER_STATE)

        fun load() =
            mapper.readValue(fp, ClusterState::class.java)
    }

    fun save() =
        mapper.writerWithDefaultPrettyPrinter().writeValue(fp, this)

 }