package com.rustyrazorblade.easycasslab.commands

import com.beust.jcommander.DynamicParameter
import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import com.github.ajalt.mordant.TermColors
import com.rustyrazorblade.easycasslab.Containers
import  com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.Docker
import  com.rustyrazorblade.easycasslab.commands.converters.AZConverter
import java.io.File
import  com.rustyrazorblade.easycasslab.terraform.Configuration
import  com.rustyrazorblade.easycasslab.containers.Terraform
import com.rustyrazorblade.easycasslab.terraform.EBSConfiguration
import com.rustyrazorblade.easycasslab.terraform.EBSType
import org.apache.logging.log4j.kotlin.logger
import java.time.LocalDate


sealed class CopyResourceResult {
    class Created(val fp: File) : CopyResourceResult()
    class Existed(val fp: File) : CopyResourceResult()
}

@Parameters(commandDescription = "Initialize this directory for easy-cass-lab")
class Init(val context: Context) : ICommand {

    @Parameter(description = "Number of Cassandra instances", names = ["--cassandra", "-c"])
    var cassandraInstances = 3

    @Parameter(description = "Number of stress instances", names = ["--stress", "-s"])
    var stressInstances = 0

    @Parameter(description = "Start instances automatically", names = ["--up"])
    var start = false

    @Parameter(description = "Instance Type.  Set EASY_CASS_LAB_INSTANCE_TYPE to set a default.", names = ["--instance"])
    var instanceType =  System.getenv("EASY_CASS_LAB_INSTANCE_TYPE") ?: "r3.2xlarge"

    @Parameter(description = "Limit to specified availability zones", names = ["--azs", "--az", "-z"], listConverter = AZConverter::class)
    var azs: List<String> = listOf()

    @Parameter(description = "Specify when the instances can be deleted", names = ["--until"])
    var until = LocalDate.now().plusDays(1).toString()

    @Parameter(description = "AMI.  Set EASY_CASS_LAB_AMI to override the default.", names = ["--ami"])
    var ami = System.getenv("EASY_CASS_LAB_AMI") ?: ""

    @Parameter(description = "Unrestricted SSH access", names = ["--open"])
    var open = false

    @Parameter(description = "EBS Volume Type", names = ["--ebs.type"])
    var ebs_type = EBSType.NONE

    @Parameter(description = "EBS Volume Size (in GB)", names = ["--ebs.size"])
    var ebs_size = 256

    @Parameter(description = "EBS Volume IOPS (note: only applies if '--ebs.type gp3'", names = ["--ebs.iops"])
    var ebs_iops = 0

    @Parameter(description = "EBS Volume Throughput (note: only applies if '--ebs.type gp3')", names = ["--ebs.throughput"])
    var ebs_throughput = 0

    @Parameter(description = "Set EBS-Optimized instance (only supported for EBS-optimized instance types", names = ["--ebs.optimized"])
    var ebs_optimized = false;

    @Parameter(description = "Cluster name")
    var name = "test"

    @DynamicParameter(names = ["--tag."], description = "Tag instances")
    var tags: Map<String, String> = mutableMapOf()

    override fun execute() {
        println("Initializing directory")
        val docker = Docker(context)
        docker.pullImage(Containers.TERRAFORM)

        val allowedTypes = listOf("m1", "m3", "t1", "c1", "c3", "cc2", "cr1", "m2", "r3", "d2", "hs1", "i2", "c5", "m5", "t3")

        if(System.getenv("EASY_CASS_LAB_SKIP_INSTANCE_CHECK") == "") {
            var found = false
            for (x in allowedTypes) {
                if (instanceType.startsWith(x))
                    found = true
            }
            if (!found) {
                throw Exception("You requested the instance type $instanceType, but unfortunately it isn't supported in EC2 Classic.  We currently only support the following classes: $allowedTypes")
            }
        }

        // Added because if we're reusing a directory, we don't want any of the previous state
        Clean().execute()

        val ebs = EBSConfiguration(ebs_type, ebs_size, ebs_iops, ebs_throughput, ebs_optimized)
        var config = Configuration(name, context.userConfig.region, context, ami, open, ebs)
        println("Directory Initialized Configuring Terraform")

        config.numCassandraInstances = cassandraInstances
        config.numStressInstances = stressInstances
        config.cassandraInstanceType = instanceType

        for ((key, value) in tags) {
            config.setTag(key, value)
        }

        config.setVariable("NeededUntil", until)

        if(azs.isNotEmpty()) {
            println("Overriding default az list with $azs")
            config.azs = expand(context.userConfig.region, azs)
        }

        println("Writing Config")
        writeTerraformConfig(config)

        println("Your workspace has been initialized with $cassandraInstances Cassandra instances (${config.cassandraInstanceType}) and $stressInstances stress instances in ${context.userConfig.region}")

        if(start) {
            println("Provisioning instances")
            Up(context).execute()
        } else {
            with(TermColors()) {
                println("Next you'll want to run ${green("easy-cass-lab up")} to start your instances.")
            }
        }
    }

    fun writeTerraformConfig(config: Configuration): Result<String> {
        val configOutput = File("terraform.tf.json")
        config.write(configOutput)

        val terraform = Terraform(context)
        println("Calling init")
        return terraform.init()
    }

    companion object {
        fun expand(region: String, azs: List<String>) : List<String> = azs.map { region + it }

        val log = logger()
    }
}