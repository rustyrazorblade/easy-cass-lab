package com.rustyrazorblade.easycasslab.terraform

import com.rustyrazorblade.easycasslab.TestContextFactory
import com.rustyrazorblade.easycasslab.commands.delegates.SparkInitParams
import com.rustyrazorblade.easycasslab.configuration.User
import com.rustyrazorblade.easycasslab.providers.aws.terraform.AWSConfiguration
import com.rustyrazorblade.easycasslab.providers.aws.terraform.EBSConfiguration
import com.rustyrazorblade.easycasslab.providers.aws.terraform.EBSType
import java.io.File
import org.assertj.core.api.AssertionsForClassTypes.assertThat
import org.junit.jupiter.api.Test

class AWSConfigurationTest {
    @Test
    fun testConfigWriteWithSparkParams() {
        val context = TestContextFactory.createTestContext()
        val userConfigFile = File(context.profileDir, "settings.yaml")
        val user = context.yaml.readValue(userConfigFile, User::class.java)

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
                user = user,
                open = false,
                ami = "test",
                sparkParams = sparkParams,
                ebs = EBSConfiguration(EBSType.NONE, 0, 0, 0, false),
            )

        assertThat(awsConfiguration).hasFieldOrProperty("sparkParams").isNotNull()
        val json = awsConfiguration.toJSON()

        assertThat(awsConfiguration.terraformConfig.resource).hasFieldOrProperty("aws_emr_cluster").isNotNull

        assertThat(json).isNotNull()
        assertThat(json).isNotEmpty()
        assertThat(json).contains("\"name\"")
        assertThat(json).contains("\"region\"")

        assertThat(json).doesNotContain("masterInstanceGroup")
        assertThat(json).contains("master_instance_group")
    }
}
