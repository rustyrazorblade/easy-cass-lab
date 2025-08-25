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
import com.rustyrazorblade.easycasslab.annotations.RequireDocker
import com.rustyrazorblade.easycasslab.commands.converters.AZConverter
import com.rustyrazorblade.easycasslab.commands.delegates.Arch
import com.rustyrazorblade.easycasslab.commands.delegates.SparkInitParams
import com.rustyrazorblade.easycasslab.configuration.ClusterState
import com.rustyrazorblade.easycasslab.configuration.InitConfig
import com.rustyrazorblade.easycasslab.containers.Terraform
import com.rustyrazorblade.easycasslab.output.OutputHandler
import com.rustyrazorblade.easycasslab.providers.aws.terraform.AWSConfiguration
import com.rustyrazorblade.easycasslab.providers.aws.terraform.EBSConfiguration
import com.rustyrazorblade.easycasslab.providers.aws.terraform.EBSType
import io.github.oshai.kotlinlogging.KotlinLogging
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.parameter.parametersOf
import java.io.File
import java.time.LocalDate

@RequireDocker
@Parameters(commandDescription = "Initialize this directory for easy-cass-lab")
class Init(
    @JsonIgnore val context: Context,
) : ICommand, KoinComponent {
    private val outputHandler: OutputHandler by inject()

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
    var ebsType = EBSType.NONE

    @Parameter(description = "EBS Volume Size (in GB)", names = ["--ebs.size"])
    var ebsSize = DEFAULT_EBS_SIZE_GB

    @Parameter(
        description = "EBS Volume IOPS (note: only applies if '--ebs.type gp3'",
        names = ["--ebs.iops"],
    )
    var ebsIops = 0

    @Parameter(
        description = "EBS Volume Throughput (note: only applies if '--ebs.type gp3')",
        names = ["--ebs.throughput"],
    )
    var ebsThroughput = 0

    @Parameter(
        description = "Set EBS-Optimized instance (only supported for EBS-optimized instance types",
        names = ["--ebs.optimized"],
    )
    var ebsOptimized = false

    @Parameter(description = "Cluster name")
    var name = "test"

    @Parameter(description = "CPU architecture", names = ["--arch", "-a", "--cpu"])
    var arch = Arch.amd64

    @ParametersDelegate
    var spark = SparkInitParams()

    @DynamicParameter(names = ["--tag."], description = "Tag instances")
    var tags: Map<String, String> = mutableMapOf()

    @Parameter(description = "Clean existing configuration before initializing", names = ["--clean"])
    var clean = false

    override fun execute() {
        validateParameters()
        
        // Check for existing files unless --clean is specified
        if (!clean) {
            checkExistingFiles()
        }
        
        prepareEnvironment()

        outputHandler.handleMessage("Initializing directory")

        val config = buildAWSConfiguration()
        configureAWSSettings(config)

        initializeTerraform(config)
        extractResourceFiles()

        displayCompletionMessage(config)

        if (start) {
            outputHandler.handleMessage("Provisioning instances")
            Up(context).execute()
        } else {
            with(TermColors()) {
                outputHandler.handleMessage(
                    "Next you'll want to run ${green("easy-cass-lab up")} to start your instances.",
                )
            }
        }
    }

    private fun validateParameters() {
        require(cassandraInstances > 0) { "Number of Cassandra instances must be positive" }
        require(stressInstances >= 0) { "Number of stress instances cannot be negative" }
        require(ebsSize > 0) { "EBS size must be positive" }
        require(ebsIops >= 0) { "EBS IOPS cannot be negative" }
        require(ebsThroughput >= 0) { "EBS throughput cannot be negative" }
    }

    private fun checkExistingFiles() {
        val existingFiles = mutableListOf<String>()
        
        // Check files from Clean.filesToClean
        Clean.filesToClean.forEach { file ->
            if (File(file).exists()) {
                existingFiles.add(file)
            }
        }
        
        // Check directories from Clean.directoriesToClean
        Clean.directoriesToClean.forEach { dir ->
            if (File(dir).exists()) {
                existingFiles.add("$dir/")
            }
        }
        
        if (existingFiles.isNotEmpty()) {
            val message = buildString {
                appendLine("Error: Directory already contains configuration files:")
                existingFiles.forEach { appendLine("  - $it") }
                appendLine()
                appendLine("Please use --clean flag to remove existing configuration, or run 'easy-cass-lab clean' first.")
            }
            outputHandler.handleMessage(message)
            System.exit(1)
        }
    }

    private fun prepareEnvironment() {
        val docker: Docker by inject { parametersOf(context) }
        docker.pullImage(Containers.TERRAFORM)

        // Only run Clean if --clean flag is provided
        if (clean) {
            outputHandler.handleMessage("Cleaning existing configuration...")
            Clean().execute()
        }

        // Create InitConfig with all the parameters from this Init command
        val initConfig = InitConfig(
            cassandraInstances = cassandraInstances,
            stressInstances = stressInstances,
            instanceType = instanceType,
            stressInstanceType = stressInstanceType,
            azs = azs,
            ami = ami,
            region = context.userConfig.region,
            name = name,
            ebsType = ebsType.toString(),
            ebsSize = ebsSize,
            ebsIops = ebsIops,
            ebsThroughput = ebsThroughput,
            ebsOptimized = ebsOptimized,
            open = open,
            controlInstances = 1,  // Control instances are currently hardcoded to 1
            controlInstanceType = "t3.xlarge",  // Default control instance type
            tags = tags,
        )

        val state = ClusterState(
            name = name,
            versions = mutableMapOf(),
            initConfig = initConfig,
        )
        state.save()
    }

    private fun buildAWSConfiguration(): AWSConfiguration {
        val ebs = EBSConfiguration(ebsType, ebsSize, ebsIops, ebsThroughput, ebsOptimized)
        return AWSConfiguration(
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
    }

    private fun configureAWSSettings(config: AWSConfiguration) {
        outputHandler.handleMessage("Directory Initialized Configuring Terraform")

        for ((key, value) in tags) {
            config.setTag(key, value)
        }

        config.setVariable("NeededUntil", until)

        if (azs.isNotEmpty()) {
            outputHandler.handleMessage("Overriding default az list with $azs")
            config.azs = expand(context.userConfig.region, azs)
        }
    }

    private fun initializeTerraform(config: AWSConfiguration) {
        outputHandler.handleMessage("Writing OpenTofu Config")
        writeTerraformConfig(config)
    }

    private fun extractResourceFiles() {
        outputHandler.handleMessage("Writing setup_instance.sh")
        extractResourceFile("setup_instance.sh", "setup_instance.sh")
        extractResourceFile("axonops-dashboards.json", "axonops-dashboards.json")

        // Create control directory and extract docker-compose.yaml and otel config there
        outputHandler.handleMessage(
            "Creating control directory and writing docker-compose.yaml and otel-collector-config.yaml",
        )
        File("control").mkdirs()
        extractResourceFile("docker-compose.yaml", "control/docker-compose.yaml")
        extractResourceFile("otel-collector-config.yaml", "control/otel-collector-config.yaml")
        
        // Create cassandra directory and extract OTel configs for Cassandra nodes
        outputHandler.handleMessage(
            "Creating cassandra directory and writing OTel configs for Cassandra nodes",
        )
        File("cassandra").mkdirs()
        extractResourceFile("otel-cassandra-config.yaml", "cassandra/otel-cassandra-config.yaml")
        extractResourceFile("docker-compose-cassandra.yaml", "cassandra/docker-compose-cassandra.yaml")
    }

    private fun extractResourceFile(
        resourceName: String,
        targetFileName: String,
    ) {
        this::class.java.getResourceAsStream(resourceName).use { stream ->
            requireNotNull(stream) { "Resource $resourceName not found" }
            File(targetFileName).outputStream().use { output ->
                stream.copyTo(output)
            }
        }
    }

    private fun displayCompletionMessage(config: AWSConfiguration) {
        outputHandler.handleMessage(
            "Your workspace has been initialized with $cassandraInstances Cassandra instances " +
                "(${config.cassandraInstanceType}) and $stressInstances stress instances " +
                "in ${context.userConfig.region}",
        )
    }

    private fun writeTerraformConfig(config: AWSConfiguration): Result<String> {
        val configOutput = File("terraform.tf.json")
        config.write(configOutput)

        val terraform = Terraform(context)
        outputHandler.handleMessage("Calling init")
        return terraform.init()
    }
}
