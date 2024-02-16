package com.rustyrazorblade.easycasslab.commands

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import com.github.ajalt.mordant.TermColors
import  com.rustyrazorblade.easycasslab.Context
import  com.rustyrazorblade.easycasslab.core.YamlDelegate
import  com.rustyrazorblade.easycasslab.configuration.*
import  com.rustyrazorblade.easycasslab.containers.CassandraUnpack
import org.apache.commons.io.FileUtils
import org.apache.logging.log4j.kotlin.logger
import java.io.File
import java.io.FileFilter
import java.io.FileNotFoundException
import java.util.*

@Parameters(commandDescription = "Use a Cassandra build")
class UseCassandra(val context: Context) : ICommand {
    @Parameter
    var name: String = ""

    val log = logger()

    @Parameter(description = "Configuration settings to change in the cassandra.yaml file specified in the format key:value,...", names = ["--config", "-c"])
    var configSettings = listOf<String>()

    val yaml by YamlDelegate()

    override fun execute() {
        check(name.isNotBlank())
        try {
            context.tfstate
        } catch (e: FileNotFoundException) {
            println("Error: terraform config file not found.  Please run easy-cass-lab up first to establish IP addresses for seed listing.")
            System.exit(1)
        }

        // setup the provisioning directory
//        val artifactDest = File("provisioning/cassandra/")

//        println("Destination artifacts: $artifactDest")
//        artifactDest.mkdirs()

        // delete existing deb packages
//        for(deb in artifactDest.listFiles(FileFilter { it.extension.equals("deb") })) {
//            deb.delete()
//        }

        // if we're been passed a version, use the debs we get from apache
        val versionRegex = """\d+\.\d+[\.~]\w+""".toRegex()

//        // update the seeds list
//        val cassandraYamlLocation = "provisioning/cassandra/conf/cassandra.yaml"
        val cassandraEnvLocation = "provisioning/cassandra/conf/cassandra-env.sh"
//        val cassandraYaml = CassandraYaml.create(File(cassandraYamlLocation))
//
        // need to move this out
//        cassandraYaml.setProperty("endpoint_snitch", "Ec2Snitch")
//
        val cassandraHosts = context.tfstate.getHosts(ServerType.Cassandra)
//        val seeds = cassandraHosts.take(3)
//
//        cassandraYaml.setSeeds(seeds.map { it.private })
//
//        configSettings.forEach {
//            val keyValue = it.split(":")
//            if (keyValue.count() > 1) {
//                cassandraYaml.setProperty(keyValue[0], keyValue[1])
//            }
//        }

//        log.debug { "Writing Cassandra YAML to $cassandraYamlLocation" }
//        cassandraYaml.write(cassandraYamlLocation)

        val stressHosts = context.tfstate.getHosts(ServerType.Stress)

        // TODO: possibly move the prometheus file generation to Up command, i'm not sure if we need to wait before generating it
        // if using a monitoring instance, set the hosts to pull metrics from
        val prometheusYamlLocation = "provisioning/monitoring/config/prometheus/prometheus.yml"
        val prometheusOutput = File(prometheusYamlLocation).outputStream()

        val labelBaseLocation = "provisioning/monitoring/config/prometheus/"

        val cassandraLabelOutput = File(labelBaseLocation, "cassandra.yml").outputStream()
        val cassandraOSLabelOutput = File(labelBaseLocation, "cassandra-os.yml").outputStream()
        val stressLabelOutput = File(labelBaseLocation, "stress.yml").outputStream()

        println("Writing prometheus configuration")
        Prometheus.writeConfiguration(cassandraHosts.map {
            HostInfo(it.private, it.alias, rack = it.availabilityZone)
        }, stressHosts.map {
            HostInfo(it.private, it.alias, rack = it.availabilityZone)
        },
                "/etc/prometheus/", prometheusOutput, cassandraLabelOutput, cassandraOSLabelOutput, stressLabelOutput)
        log.debug { "Writing Prometheus YAML to $prometheusYamlLocation" }

        // write out the sd file
        // val env = File(cassandraEnvLocation)
        // env.appendText("\nJVM_OPTS=\"\$JVM_OPTS -Dcassandra.consistent.rangemovement=false -XX:+UnlockDiagnosticVMOptions -XX:+DebugNonSafepoints -XX:+PreserveFramePointer \"\n")

//        with(TermColors()) {
//            println("Cassandra deb and config copied to provisioning/.  Config files are located in provisioning/cassandra. \n Use ${green("easy-cass-lab install")} to push the artifacts to the nodes.")
//        }
    }
}