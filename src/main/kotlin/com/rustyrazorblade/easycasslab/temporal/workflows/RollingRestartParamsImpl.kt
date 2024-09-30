package com.rustyrazorblade.easycasslab.temporal.workflows

import com.fasterxml.jackson.annotation.JsonProperty

class RollingRestartParamsImpl(@JsonProperty("ips") private val ips: List<String>, @JsonProperty("ssh_key") private val sshKeyPath: String) : RollingRestartParams {
    override fun getIps(): List<String> {
        return ips
    }

    override fun getSSHKeyPath(): String {
        return sshKeyPath
    }
}