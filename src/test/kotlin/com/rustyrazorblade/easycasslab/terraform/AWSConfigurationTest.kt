package com.rustyrazorblade.easycasslab.terraform

import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.commands.delegates.SparkInitParams
import com.rustyrazorblade.easycasslab.providers.aws.terraform.AWSConfiguration
import com.rustyrazorblade.easycasslab.providers.aws.terraform.EBSConfiguration
import com.rustyrazorblade.easycasslab.providers.aws.terraform.EBSType
import org.assertj.core.api.AssertionsForClassTypes.assertThat
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AWSConfigurationTest {
    @Test
    fun testConfigWriteWithSparkParams() {
        val context = Context.testContext()

        val sparkParams =
            SparkInitParams().apply {
                enable = true
                masterInstanceType = "m5.large"
                workerInstanceType = "c5.xlarge"
                workerCount = 5
            }

        val awsConfiguration =
            AWSConfiguration(
                name = "test-cluster",
                region = "us-west-2",
                context = context,
                open = false,
                ami = "test",
                sparkParams = sparkParams,
                ebs = EBSConfiguration(EBSType.NONE, 0, 0, 0, false),
            )

        assertThat(awsConfiguration).hasFieldOrProperty("sparkParams").isNotNull()
        val json = awsConfiguration.toJSON()

        assertThat(awsConfiguration.terraformConfig.resource).hasFieldOrProperty("aws_emr_cluster").isNotNull

        assertNotNull(json)
        assertTrue(json.isNotEmpty())
        assertTrue(json.contains("\"name\""))
        assertTrue(json.contains("\"region\""))

        assertThat(json).doesNotContain("masterInstanceGroup")
        assertThat(json).contains("master_instance_group")
    }
}
