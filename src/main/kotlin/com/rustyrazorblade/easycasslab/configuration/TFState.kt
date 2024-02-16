package com.rustyrazorblade.easycasslab.configuration

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.DeserializationFeature
import  com.rustyrazorblade.easycasslab.Context
import org.apache.logging.log4j.kotlin.logger
import java.io.BufferedWriter
import java.io.File
import java.io.InputStream
import java.util.LinkedHashMap
import com.fasterxml.jackson.module.kotlin.readValue

typealias HostList = List<Host>

class TFState(val context: Context,
              val file: InputStream) {

    @JsonIgnoreProperties
    data class StateFile(val resources: List<Resource>)

    data class Resource(
        val mode: String,
        val name: String,
        val instances: List<Instance>

    )
    data class Instance(
        val attributes: Attributes
    )
    // security groups don't have an IP.
    // we end up throwing exceptions if we don't make these fields nullable
    data class Attributes (
            val private_ip: String?,
            val public_ip: String?,
            var availability_zone: String?
    )

    private var log = logger()
    companion object {
        fun parse(context: Context, path: File) : TFState {
            return TFState(context, path.inputStream())
        }
    }

    // we put this here for two reasons
    // the first is we don't want to process it more than once
    // the second is we don't want to process if we don't read the state
    val state by lazy {
        log.info("Loading tfstate from $file")
        val json = context.getJsonMapper()
        json.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        val state : StateFile = json.readValue(file)
        state
    }

    fun getHosts(serverType: ServerType) : HostList {
        // resources [cassandra, stress, monitoring]

        val matching = state.resources.firstOrNull { it.name == serverType.serverType }


        val hosts = matching?.instances?.mapIndexed { index, instance ->
            Host(instance.attributes.public_ip ?: "",
                    instance.attributes.private_ip ?: "",
                    serverType.serverType + index,
                    instance.attributes.availability_zone ?: "") }  ?: listOf()

        return hosts

//        resources.path("instances")
//
//        val result = mutableListOf<Host>()
//        val resources2 = context.json.convertValue(nodes, Map::class.java)
//
//        log.info("Resources $resources")
//
//        for((name, resource) in resources.entries) {
//            resource as Map<String, *>
//
//            val attrs = (resource.get("primary") as LinkedHashMap<*, *>).get("attributes") as LinkedHashMap<String, String>
//
//            val private = attrs.get("private_ip")
//            val public = attrs.get("public_ip")
//            val az = attrs.get("availability_zone")
//
//            val serverName = name as String?
//
//            if(public != null && private != null && serverName != null) {
//
//                if(serverName.contains(serverType.serverType)) {
//                    val host = Host.fromTerraformString(serverName as String, public, private, az as String)
//                    log.info { "Adding host: $host" }
//                    result.add(host)
//                }
//            } else {
//                log.error("Invalid terraform state: null IP for $serverName, check terraform.tfstate to debug.")
//            }
//
//        }
    }

    fun writeSshConfig(config: BufferedWriter) {
        // write standard stuff first
        config.appendLine("StrictHostKeyChecking=no")
        config.appendLine("User ubuntu")
        config.appendLine("IdentityFile ${context.userConfig.sshKeyPath}")

        // get each server type and get the hosts for type and add it to the sshConfig.
        ServerType.values().forEach {
            getHosts(it).forEach {
                config.appendLine("Host ${it.alias}")
                config.appendLine(" Hostname ${it.public}")
                config.appendLine()
            }
        }
        config.flush()
    }

    fun writeEnvironmentFile(fp: BufferedWriter) {

        // write the initial SSH aliases

        fp.appendLine("#!/bin/bash")
        fp.appendLine()

        var i = 0
        fp.append("SERVERS=(")
        getHosts(ServerType.Cassandra).forEach {
            fp.append("cassandra$i ")
            i++
        }
        fp.appendLine(")")

        i=0
        getHosts(ServerType.Cassandra).forEach {
            fp.appendLine("alias c${i}=\"ssh cassandra${i}\"")
            i++
        }

        fp.appendLine()

        val content = this.javaClass.getResourceAsStream("env.sh").bufferedReader()
        val lines = content.readLines().toMutableList()

        for(line in lines) {
            fp.appendLine(line)
        }
        fp.flush()
    }


}