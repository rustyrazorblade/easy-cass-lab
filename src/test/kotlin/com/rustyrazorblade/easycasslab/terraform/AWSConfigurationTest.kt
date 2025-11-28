package com.rustyrazorblade.easycasslab.terraform

import com.rustyrazorblade.easycasslab.Constants
import com.rustyrazorblade.easycasslab.TestContextFactory
import com.rustyrazorblade.easycasslab.commands.mixins.SparkInitMixin
import com.rustyrazorblade.easycasslab.configuration.User
import com.rustyrazorblade.easycasslab.providers.aws.terraform.AWSConfiguration
import com.rustyrazorblade.easycasslab.providers.aws.terraform.EBSConfiguration
import com.rustyrazorblade.easycasslab.providers.aws.terraform.EBSType
import org.assertj.core.api.AssertionsForClassTypes.assertThat
import org.junit.jupiter.api.Test
import java.io.File

class AWSConfigurationTest {
    @Test
    fun testConfigWriteWithSparkParams() {
        val context = TestContextFactory.createTestContext()
        val userConfigFile = File(context.profileDir, "settings.yaml")
        val user = context.yaml.readValue(userConfigFile, User::class.java)

        val sparkParams =
            SparkInitMixin().apply {
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
                accountId = "123456789012",
                clusterId = "test-cluster-id",
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

    @Test
    fun testIAMInstanceProfileIncludedInAllInstances() {
        val context = TestContextFactory.createTestContext()
        val userConfigFile = File(context.profileDir, "settings.yaml")
        val user = context.yaml.readValue(userConfigFile, User::class.java)

        val awsConfiguration =
            AWSConfiguration(
                name = "test-cluster",
                region = "us-west-2",
                context = context,
                user = user,
                open = false,
                ami = "test",
                sparkParams = SparkInitMixin(),
                ebs = EBSConfiguration(EBSType.NONE, 0, 0, 0, false),
                accountId = "123456789012",
                numCassandraInstances = 1,
                numStressInstances = 1,
                clusterId = "test-cluster-id",
            )

        val json = awsConfiguration.toJSON()

        // Verify JSON is generated
        assertThat(json).isNotNull()
        assertThat(json).isNotEmpty()

        // Verify Cassandra instance has IAM instance profile
        assertThat(json).contains("\"cassandra0\"")
        assertThat(json).contains("\"iam_instance_profile\" : \"${Constants.AWS.Roles.EC2_INSTANCE_ROLE}\"")

        // Verify Stress instance has IAM instance profile
        assertThat(json).contains("\"stress0\"")

        // Verify Control instance has IAM instance profile
        assertThat(json).contains("\"control0\"")

        // Verify all instances reference the IAM role
        val instanceProfileCount = json.split("\"iam_instance_profile\" : \"${Constants.AWS.Roles.EC2_INSTANCE_ROLE}\"").size - 1
        assertThat(instanceProfileCount).isGreaterThanOrEqualTo(3) // At least Cassandra, Stress, Control
    }
}
