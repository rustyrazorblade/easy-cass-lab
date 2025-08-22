@file:Suppress("VariableNaming")

package com.rustyrazorblade.easycasslab.commands

import com.beust.jcommander.DynamicParameter
import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import com.beust.jcommander.ParametersDelegate
import com.fasterxml.jackson.annotation.JsonIgnore
import com.github.ajalt.mordant.TermColors
import com.rustyrazorblade.easycasslab.Containers
import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.Docker
import com.rustyrazorblade.easycasslab.commands.converters.AZConverter
import com.rustyrazorblade.easycasslab.commands.delegates.Arch
import com.rustyrazorblade.easycasslab.commands.delegates.SparkInitParams
import com.rustyrazorblade.easycasslab.configuration.ClusterState
import com.rustyrazorblade.easycasslab.containers.Terraform
import com.rustyrazorblade.easycasslab.providers.aws.terraform.AWSConfiguration
import com.rustyrazorblade.easycasslab.providers.aws.terraform.EBSConfiguration
import com.rustyrazorblade.easycasslab.providers.aws.terraform.EBSType
import io.github.oshai.kotlinlogging.KotlinLogging
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.parameter.parametersOf
import java.io.File
import java.time.LocalDate

@Parameters(commandDescription = "Initialize this directory for easy-cass-lab")
class Init(
    @JsonIgnore val context: Context,
) : ICommand, KoinComponent {
    companion object {
        private const val DEFAULT_CASSANDRA_INSTANCE_COUNT = 3
        private const val DEFAULT_EBS_SIZE_GB = 256

        @JsonIgnore
        val log = KotlinLogging.logger {}

        fun expand(
            region: String,
            azs: List<String>,
        ): List<String> = azs.map { region + it }
    }

    @Parameter(description = "Number of Cassandra instances", names = ["--cassandra", "-c"])
    var cassandraInstances = DEFAULT_CASSANDRA_INSTANCE_COUNT

    @Parameter(description = "Number of stress instances", names = ["--stress", "-s"])
    var stressInstances = 0

    @Parameter(description = "Start instances automatically", names = ["--up"])
    var start = false

    @Parameter(
        description = "Instance Type.  Set EASY_CASS_LAB_INSTANCE_TYPE to set a default.",
        names = ["--instance", "-i"],
    )
    var instanceType = System.getenv("EASY_CASS_LAB_INSTANCE_TYPE") ?: "r3.2xlarge"

    // update to use the default stress instance type for the arch
    @Parameter(
        description = "Stress Instance Type.  Set EASY_CASS_LAB_STRESS_INSTANCE_TYPE to set a default.",
        names = ["--stress-instance", "-si", "--si"],
    )
    var stressInstanceType = System.getenv("EASY_CASS_LAB_STRESS_INSTANCE_TYPE") ?: "c7i.2xlarge"

    @Parameter(
        description = "Limit to specified availability zones",
        names = ["--azs", "--az", "-z"],
        listConverter = AZConverter::class,
    )
    var azs: List<String> = listOf()

    @Parameter(description = "Specify when the instances can be deleted", names = ["--until"])
    var until = LocalDate.now().plusDays(1).toString()

    @Parameter(
        description = "AMI.  Set EASY_CASS_LAB_AMI to override the default.",
        names = ["--ami"],
    )
    var ami = System.getenv("EASY_CASS_LAB_AMI") ?: ""

    @Parameter(description = "Unrestricted SSH access", names = ["--open"])
    var open = false

    @Parameter(description = "EBS Volume Type", names = ["--ebs.type"])
    var ebs_type = EBSType.NONE

    @Parameter(description = "EBS Volume Size (in GB)", names = ["--ebs.size"])
    var ebs_size = DEFAULT_EBS_SIZE_GB

    @Parameter(
        description = "EBS Volume IOPS (note: only applies if '--ebs.type gp3'",
        names = ["--ebs.iops"],
    )
    var ebs_iops = 0

    @Parameter(
        description = "EBS Volume Throughput (note: only applies if '--ebs.type gp3')",
        names = ["--ebs.throughput"],
    )
    var ebs_throughput = 0

    @Parameter(
        description = "Set EBS-Optimized instance (only supported for EBS-optimized instance types",
        names = ["--ebs.optimized"],
    )
    var ebs_optimized = false

    @Parameter(description = "Cluster name")
    var name = "test"

    @Parameter(description = "CPU architecture", names = ["--arch", "-a", "--cpu"])
    var arch = Arch.amd64

    @ParametersDelegate
    var spark = SparkInitParams()

    @DynamicParameter(names = ["--tag."], description = "Tag instances")
    var tags: Map<String, String> = mutableMapOf()

    override fun execute() {
        println("Initializing directory")
        val docker: Docker by inject { parametersOf(context) }
        docker.pullImage(Containers.TERRAFORM)

        // Added because if we're reusing a directory, we don't want any of the previous state
        Clean().execute()

        val state = ClusterState(name = name, versions = mutableMapOf())
        state.save()

        val ebs = EBSConfiguration(ebs_type, ebs_size, ebs_iops, ebs_throughput, ebs_optimized)
        val config =
            AWSConfiguration(
                name,
                region = context.userConfig.region,
                context = context,
                ami = ami,
                open = open,
                ebs = ebs,
                numCassandraInstances = cassandraInstances,
                cassandraInstanceType = instanceType,
                numStressInstances = stressInstances,
                stressInstanceType = stressInstanceType,
                arch = arch,
                sparkParams = spark,
            )

        println("Directory Initialized Configuring Terraform")

        for ((key, value) in tags) {
            config.setTag(key, value)
        }

        config.setVariable("NeededUntil", until)

        if (azs.isNotEmpty()) {
            println("Overriding default az list with $azs")
            config.azs = expand(context.userConfig.region, azs)
        }

        println("Writing OpenTofu Config")
        writeTerraformConfig(config)

        println("Writing setup_instance.sh")
        this::class.java.getResourceAsStream("setup_instance.sh").use { stream ->
            requireNotNull(stream) { "Resource setup_instance.sh not found" }
            val diskSetup = File("setup_instance.sh").bufferedWriter()
            diskSetup.write(stream.readBytes().toString(Charsets.US_ASCII))
            diskSetup.flush()
            diskSetup.close()
        }

        this::class.java.getResourceAsStream("axonops-dashboards.json").use { stream ->
            requireNotNull(stream) { "Resource axonops-dashboards.json not found" }
            val diskSetup = File("axonops-dashboards.json").bufferedWriter()
            diskSetup.write(stream.readBytes().toString(Charsets.US_ASCII))
            diskSetup.flush()
            diskSetup.close()
        }

        println(
            "Your workspace has been initialized with $cassandraInstances Cassandra instances " +
                "(${config.cassandraInstanceType}) and $stressInstances stress instances " +
                "in ${context.userConfig.region}",
        )
        if (start) {
            println("Provisioning instances")
            Up(context).execute()
        } else {
            with(TermColors()) {
                println("Next you'll want to run ${green("easy-cass-lab up")} to start your instances.")
            }
        }
    }

    fun writeTerraformConfig(config: AWSConfiguration): Result<String> {
        val configOutput = File("terraform.tf.json")
        config.write(configOutput)

        val terraform = Terraform(context)
        println("Calling init")
        return terraform.init()
    }
}
