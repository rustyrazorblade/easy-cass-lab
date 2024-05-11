package com.rustyrazorblade.easycasslab.terraform

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.configuration.ServerType
import org.apache.logging.log4j.kotlin.logger
import software.amazon.awssdk.annotations.Mutable
import java.io.File
import java.net.URL


/**
 * Terraform variable that gets referenced within the config.
 */
data class Variable(val default: Any?, val type: String? = null)

/**
 * Top level configuration class. Holds the TerraformConfig that's actually written out.
 */
class AWSConfiguration(var name: String,
                       var region: String,
                       var context: Context,
                       val ami: Ami,
                       val open: Boolean,
                       val ebs: EBSConfiguration,
                       var numCassandraInstances : Int = 3,
                       var cassandraInstanceType : String = "m5d.xlarge",
                       var numStressInstances : Int = 0,
                       var stressInstanceType : String = "c7i.2xlarge"
    ) {

    val logger = logger()
    private val mapper = ObjectMapper()
    private var regions = mapOf(
        "us-west-2" to listOf("us-west-2a", "us-west-2b", "us-west-2c"),
        "us-east-1" to listOf("us-east-1a", "us-east-1b", "us-east-1c"),
    )

    private val tags = mutableMapOf(
        "email" to context.userConfig.email,
        "easy_cass_lab" to "1",
        "cluster_name" to name
    )

    var azs = regions[region]!!

    private val config = TerraformConfig(region, context.userConfig.awsAccessKey,
        context.userConfig.awsSecret,
        name, azs=azs, tags=tags)

    init {
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL)
        mapper.enable(SerializationFeature.INDENT_OUTPUT)

        setVariable("email", context.userConfig.email)
        setVariable("key_name", context.userConfig.keyName)
        setVariable("key_path", context.userConfig.sshKeyPath)
        setVariable("region", region)
        setVariable("name", name)
    }

    fun setVariable(key: String, default: String?): AWSConfiguration {
        config.variable[key] = Variable(default)
        return this
    }

    fun setVariable(key: String, variable: Variable): AWSConfiguration {
        config.variable[key] = variable
        return this
    }

    fun setTag(key: String, value: String): AWSConfiguration {
        tags[key] = value
        return this
    }

    private fun getExternalIpAddress(): String {
        return URL("https://api.ipify.org/").readText()
    }


    private fun build(): AWSConfiguration {
        // use the open CIDR, or just the external address
        // if we're using open we short circuit the call to get external
        val sshRule = listOf(if (open) "0.0.0.0/0" else "${getExternalIpAddress()}/32")

        val name = "easy_cass_lab_${name}"
        val instanceSg = SecurityGroupResource(
            name,
            description = "easy-cass-lab security group",
            tags = tags,
            vpc = config.vpc,
            ingress = listOf(
                SecurityGroupRule(
                    description = "ssh",
                    from_port = 22,
                    to_port = 22,
                    protocol = TCP,
                    cidr_blocks = sshRule
                ),
                SecurityGroupRule(
                    description = "Intra node",
                    from_port = 0,
                    to_port = 65535,
                    protocol = TCP,
                    cidr_blocks = listOf("10.0.0.0/16")
                )
            ),
            egress = listOf(
                SecurityGroupRule(
                    description = "Outbound All Traffic",
                    from_port = 0,
                    to_port = 0,
                    protocol = "-1",
                    cidr_blocks = listOf("0.0.0.0/0"))
            )
        )

        config.resource.aws_security_group[instanceSg.name] = instanceSg

        val ebsConf = if (ebs.type != EBSType.NONE) ebs.createEbsConf() else null

        // tags should be standard across all resources
        val subnets = config.subnets.values.toList()
        logger.info("Using subnets: $subnets")
        var subnetPos = 0

        for (i in 0..<numCassandraInstances) {
            val instanceName = ServerType.Cassandra.serverType + i

            val cass = InstanceResource(
                ami=ami,
                instance_type=cassandraInstanceType,
                tags=tags + Pair("Name", instanceName),
                vpc_security_group_ids=listOf(instanceSg.id()),
                count=1,
                ebs_block_device=ebsConf,
                ebs_optimized=ebs.optimized_instance,
                subnet = subnets[subnetPos]
            )
            config.resource.aws_instance[instanceName] = cass

            subnetPos++
            if(subnetPos == subnets.size)
                subnetPos = 0
            logger.info("Creating resource $instanceName as $cass")
        }

        subnetPos = 0

        for (i in 0..<numStressInstances) {
            val instanceName = ServerType.Stress.serverType + i
            val stress = InstanceResource(
                ami=ami,
                instance_type = stressInstanceType,
                tags=tags + Pair("Name", instanceName),
                vpc_security_group_ids = listOf(instanceSg.id()),
                count = 1,
                ebs_block_device = null,
                ebs_optimized = false,
                subnet = subnets[subnetPos]
            )

            config.resource.aws_instance[instanceName] = stress
            subnetPos++

            if(subnetPos == subnets.size)
                subnetPos = 0
            logger.info("Creating resource $instanceName as $stress")
        }

        return this
    }

    fun toJSON(): String {
        build()
        return mapper.writeValueAsString(config)
    }

    fun write(f: File) {
        build()
        mapper.writeValue(f, config)
    }

    companion object {
        fun readTerraformConfig(f: File): TerraformConfig {
            val mapper = ObjectMapper().registerKotlinModule()
            return mapper.readValue(f, TerraformConfig::class.java)
        }
    }
}

/**
 * Top level Terraform config
 */
class TerraformConfig(@JsonIgnore val region: String = "",
                      @JsonIgnore val accessKey: String = "",
                      @JsonIgnore val secret: String = "",
                      @JsonIgnore val name: String = "easy_cass_lab",
                      @JsonIgnore val azs: List<String>,
                      @JsonIgnore val tags: Map<String, String>
) {


    var variable = mutableMapOf<String, Variable>()
    val provider = mutableMapOf("aws" to Provider(region, accessKey, secret))

    // resource is the container for all the things
    // this is completely driven by Terraform's JSON configuration file

    @JsonIgnore val vpc = VPC(name, tags = tags)
    @JsonIgnore val ig = IGW(vpc)
    @JsonIgnore val subnets : Map<String, Subnet> = azs.mapIndexed { index, s ->
        "sub${index}" to Subnet(name="sub${index}",
                                vpc=vpc,
                                cidr_block = "10.0.$index.0/24",
                                availability_zone=s,
                                tags=tags)
    }.toMap()

    @JsonIgnore
    val routeTable: RouteTable = RouteTable(name="default", vpc=vpc, igw = ig)

    @JsonIgnore
    val routes: MutableMap<String, Route> = mutableMapOf("out" to Route("0.0.0.0/0",
                                                            gateway = ig,
                                                            routeTable = routeTable))
    @JsonIgnore
    val routeTableAssociation = subnets.map {
        (name, sub) -> name to RouteTableAssociation(subnet=sub, routeTable=routeTable)
    }.toMap()

    // All AWS resources fall under this block
    val resource = AWSResource(
        aws_vpc = mapOf(name to vpc),
        aws_internet_gateway = mapOf(name to ig),
        aws_subnet = subnets,
        aws_instance = mutableMapOf(),
        aws_security_group = mutableMapOf(),
        aws_route_table = mapOf("default" to routeTable),
        aws_route = routes,
        aws_route_table_association = routeTableAssociation,
    )


    val data = object {
        val aws_ami = object {
            val image = object {
                val most_recent = true
                val filter = listOf(
                    object {
                        val name = "name"
                        val values = listOf("rustyrazorblade/images/easy-cass-lab-cassandra-*")
                    },
                    object {
                        val name = "virtualization-type"
                        val values = listOf("hvm")
                    }
                )
                val owners = listOf("self")
            }
        }
    }
}

