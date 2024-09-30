package com.rustyrazorblade.easycasslab.temporal.workflows

import com.fasterxml.jackson.databind.annotation.JsonDeserialize

@JsonDeserialize(`as` = RollingRestartParamsImpl::class)
interface RollingRestartParams {
    fun getIps(): List<String>

    fun getSSHKeyPath(): String
}