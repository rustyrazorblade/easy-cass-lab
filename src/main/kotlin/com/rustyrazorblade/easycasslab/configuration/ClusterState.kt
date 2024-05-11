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
data class ClusterState(
    var name: String,
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