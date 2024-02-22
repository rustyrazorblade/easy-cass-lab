package com.rustyrazorblade.easycasslab.commands

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import com.github.ajalt.mordant.TermColors
import com.rustyrazorblade.easycasslab.Containers
import  com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.Docker
import  com.rustyrazorblade.easycasslab.commands.converters.AZConverter
import  com.rustyrazorblade.easycasslab.configuration.Dashboards
import org.reflections.Reflections
import org.reflections.scanners.ResourcesScanner
import java.io.File
import org.apache.commons.io.FileUtils
import  com.rustyrazorblade.easycasslab.terraform.Configuration
import  com.rustyrazorblade.easycasslab.containers.Terraform
import org.apache.logging.log4j.kotlin.logger
import java.time.LocalDate
import java.util.zip.GZIPInputStream


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

    @Parameter(description = "Instance Type", names = ["--instance"])
    var instanceType =  "r3.2xlarge"

    @Parameter(description = "Limit to specified availability zones", names = ["--azs", "--az", "-z"], listConverter = AZConverter::class)
    var azs: List<String> = listOf()

    @Parameter(description = "Specify when the instances can be deleted", names = ["--until"])
    var until = LocalDate.now().plusDays(1).toString()

    @Parameter(description = "AMI.  Set EASY_CASS_LAB_AMI to set a default.", names = ["--ami"])
    var ami = System.getenv("EASY_CASS_LAB_AMI") ?: ""

    @Parameter(description = "Cluster name")
    var name = "test"

    override fun execute() {
        println("Initializing directory")
        val docker = Docker(context)
        docker.pullImage(Containers.TERRAFORM)
        docker.pullImage(Containers.PSSH)

        check(ami.isNotBlank())

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

        // copy provisioning over

        println("Copying provisioning files")

        var config = initializeDirectory(name, ami)
        println("Directory Initialized Configuring Terraform")

        config.numCassandraInstances = cassandraInstances
        config.numStressInstances = stressInstances
        config.cassandraInstanceType = instanceType

        config.setVariable("client", "")
        config.setVariable("purpose", "")
        config.setVariable("NeededUntil", until)

        if(azs.isNotEmpty()) {
            println("Overriding default az list with $azs")
            config.azs = expand(context.userConfig.region, azs)
        }

        println("Writing Config")
        writeTerraformConfig(config)

        println("Your workspace has been initialized with $cassandraInstances Cassandra instances (${config.cassandraInstanceType}) and $stressInstances stress instances in ${context.userConfig.region}")

        if(start) {
            Up(context).execute()
        } else {
            with(TermColors()) {
                println("Next you'll want to run ${green("easy-cass-lab up")} to start your instances.")
            }
        }
    }


    fun initializeDirectory(name: String, ami: String) : Configuration {
        val reflections = Reflections("com.rustyrazorblade.easycasslab.commands.origin", ResourcesScanner())
        val provisioning = reflections.getResources(".*".toPattern())

        for (f in provisioning) {
            val input = this.javaClass.getResourceAsStream("/" + f)
            val outputFile = f.replace("com/rustyrazorblade/easycasslab/commands/origin/", "")

            val output = File(outputFile)
            println("Writing ${output.absolutePath}")

            output.absoluteFile.parentFile.mkdirs()
            FileUtils.copyInputStreamToFile(input, output)
        }

        // gunzip the collector
        val collector = "collector-0.11.1-SNAPSHOT.jar.gz"

        val dir = "provisioning/cassandra/"

        println("Copying JMX collector")

        val fp = GZIPInputStream(File(dir, collector).inputStream())

        val out = File(dir, collector.removeSuffix(".gz"))

        out.writeBytes(fp.readBytes())


        return Configuration(name, context.userConfig.region, context, ami)
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