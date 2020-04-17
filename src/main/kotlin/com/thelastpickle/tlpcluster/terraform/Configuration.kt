package com.thelastpickle.tlpcluster.terraform

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.thelastpickle.tlpcluster.Context
import com.thelastpickle.tlpcluster.configuration.ServerType
import com.thelastpickle.tlpcluster.ubuntu.Regions
import java.io.File
import java.net.URL

class Configuration(val ticket: String,
                    val client: String,
                    val purpose: String,
                    var region: String,
                    var context: Context) {

    val regionLookup = Regions.load()

    var numCassandraInstances = 3
    var email = context.userConfig.email

    val tags = mutableMapOf("ticket" to ticket,
        "client" to client,
        "purpose" to purpose,
        "email" to email)

    var cassandraInstanceType = "m5d.xlarge"

    // stress
    var numStressInstances = 0

    var stressInstanceType = "c3.2xlarge"

    //monitoring
    var monitoringInstanceType = "c3.2xlarge"

    var regionObj = regionLookup.get(region)!!

    var monitoringAMI = regionObj.getAmi(monitoringInstanceType)

    private val config  = TerraformConfig(region, context.userConfig.awsAccessKey, context.userConfig.awsSecret)

    val mapper = ObjectMapper()

    init {
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL)
        mapper.enable(SerializationFeature.INDENT_OUTPUT)
    }


    var azs = regionLookup.getAzs(region)

    fun setVariable(key: String, default: String?) : Configuration {
        config.variable[key] = Variable(default)
        return this
    }

    fun setVariable(key: String, variable: Variable) : Configuration {
        config.variable[key] = variable
        return this
    }

    private fun getExternalIpAddress() : String {
        return URL("http://api.ipify.org/").readText()
    }

    private fun setInstanceResource(key: String,
                                    ami: String,
                                    instanceType: String,
                                    count: Int,
                                    securityGroups: List<String>,
                                    tags: Map<String, String>) : Configuration {
        val conf = InstanceResource(ami, instanceType, tags, security_groups = securityGroups, count = count)
        config.resource.aws_instance[key] = conf
        return this
    }

    private fun setSecurityGroupResource(securityGroup: SecurityGroupResource) : Configuration {
        config.resource.aws_security_group[securityGroup.name] = securityGroup
        return this
    }

    private fun setTagName(tags: Map<String, String>, nodeType: ServerType) : MutableMap<String, String> {
        val newTags = HashMap<String, String>(tags).toMutableMap()
        newTags["Name"] = "${ticket}_${nodeType.serverType}"
        return newTags
    }


    private fun build() : Configuration {
        val regionObj = regionLookup.get(region)!!

        var cassandraAMI = regionObj.getAmi(cassandraInstanceType)
        val stressAMI = regionObj.getAmi(stressInstanceType)

        setVariable("email", email)
        setVariable("key_name", context.userConfig.keyName)
        setVariable("key_path", context.userConfig.sshKeyPath)
        setVariable("region", region)



        setVariable("zones", Variable(azs))

        val externalCidr = listOf("${getExternalIpAddress()}/32")

        val unixTime = System.currentTimeMillis() / 1000L

        val instanceSg = SecurityGroupResource.Builder()
            .newSecurityGroupResource("${ticket}_TlpClusterSG_$unixTime","tlp-cluster ${ticket} security group", tags)
            //.withOutboundRule(0, 65535, "tcp", listOf("0.0.0.0/0"), "All traffic")
            .withInboundRule(22, 22, "tcp", externalCidr, "SSH")
            .withInboundSelfRule(0, 65535, "tcp", "Intra node")
            .withInboundRule(9090, 9090, "tcp", externalCidr, "Prometheus GUI")
            .withInboundRule(3000, 3000, "tcp", externalCidr, "Grafana GUI")
            .withInboundRule(8080, 8080, "tcp", externalCidr, "Reaper GUI")
            .build()

        setSecurityGroupResource(instanceSg)

        setInstanceResource(
            "cassandra",
            cassandraAMI,
            cassandraInstanceType,
            numCassandraInstances,
            listOf(instanceSg.name),
            setTagName(tags, ServerType.Cassandra))
        setInstanceResource(
            "stress",
            stressAMI,
            stressInstanceType,
            numStressInstances,
            listOf(instanceSg.name),
            setTagName(tags, ServerType.Stress))
        setInstanceResource(
            "monitoring",
            monitoringAMI,
            monitoringInstanceType,
            1, // we always enable monitoring now
            listOf(instanceSg.name),
            setTagName(tags, ServerType.Monitoring))

        return this
    }

    fun toJSON() : String {
        build()
        return mapper.writeValueAsString(config)
    }

    fun write(f: File) {
        build()
        mapper.writeValue(f, config)
    }
}

class TerraformConfig(@JsonIgnore val region: String,
                      @JsonIgnore val accessKey: String,
                      @JsonIgnore val secret: String) {

    var variable = mutableMapOf<String, Variable>()
    val provider = mutableMapOf("aws" to Provider(region, accessKey, secret))
    val resource = AWSResource()
}

data class Provider(val region: String,
                    val access_key: String,
                    val secret_key: String)

data class Variable(val default: Any?, val type: String? = null)

data class InstanceResource(
    val ami: String = "ami-5153702",
    val instance_type: String = "m5d.xlarge",
    val tags: Map<String, String> = mapOf(),
    val security_groups : List<String> = listOf(),
    val key_name : String = "\${var.key_name}",
    val availability_zone: String = "\${element(var.zones, count.index)}",
    val count : Int
)

data class SecurityGroupRule(
    val description: String,
    val from_port : Int,
    val to_port: Int,
    val protocol: String = "tcp",
    val self: Boolean = false,
    val cidr_blocks: List<String> = listOf(),
    val ipv6_cidr_blocks: List<String> = listOf(),
    val prefix_list_ids: List<String> = listOf(),
    val security_groups: List<String> = listOf()


)

data class SecurityGroupResource(
    val name: String,
    val description : String,
    val tags: Map<String, String>,
    val ingress: List<SecurityGroupRule>
) {
    class Builder {
        private var name: String = ""
        private var description: String = ""
        private var tags: Map<String, String> = mutableMapOf()
        private var ingress: MutableList<SecurityGroupRule> = mutableListOf()
//        private var egress: MutableList<SecurityGroupRule> = mutableListOf()

        fun newSecurityGroupResource(name: String, description: String, tags: Map<String, String>) : Builder {
            this.name = name
            this.description = description
            this.tags = tags

            return this
        }

        fun withInboundSelfRule(from_port: Int, to_port: Int, protocol: String, description: String) : Builder {
            this.ingress.add(SecurityGroupRule(description, from_port, to_port, protocol, self = true))
            return this
        }

        fun withInboundRule(
            from_port: Int,
            to_port: Int,
            protocol: String,
            cidr_blocks: List<String>,
            description: String) : Builder {

            this.ingress.add(SecurityGroupRule(description, from_port, to_port, protocol, cidr_blocks = cidr_blocks))
            return this
        }


        fun build () = SecurityGroupResource(name, description, tags, ingress)
    }
}

data class AWSResource(
    var aws_instance : MutableMap<String, InstanceResource> = mutableMapOf(),
    var aws_security_group : MutableMap<String, SecurityGroupResource> = mutableMapOf()
)
