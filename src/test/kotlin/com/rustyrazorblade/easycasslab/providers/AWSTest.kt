package com.rustyrazorblade.easycasslab.providers

import com.rustyrazorblade.easycasslab.Context
import org.junit.jupiter.api.Test

internal class AWSTest {
    private val context = Context.testContext()
    private val aws = context.cloudProvider

    @Test
    fun createEMRServiceRoleSuccess() {
        aws.createServiceRole()
    }
}
