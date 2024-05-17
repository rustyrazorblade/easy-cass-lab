package com.rustyrazorblade.easycasslab.terraform

import com.fasterxml.jackson.annotation.JsonIgnore

typealias Ami = String
const val TCP = "tcp"

enum class EBSType(val type: String) {
    NONE(""),
    GP2("gp2"),
    GP3("gp3")
}

data class EBSConfiguration(
    val type: EBSType,
    val size: Int,
    val iops: Int,
    val throughput: Int,
    val optimized_instance: Boolean) {

    fun createEbsConf() =
        InstanceEBSBlockDevice(
            volume_type = type.type,
            volume_size = size,
            iops = if (type != EBSType.GP3) 0 else iops,
            throughput = if (type != EBSType.GP3) 0 else throughput
        )

}


data class Provider(val region: String,
                    val shared_credentials_files: List<String>,
                    val profile: String = "default")

data class InstanceResource(
    var ami: String = "",
    val instance_type: String = "m5d.xlarge",
    val tags: Map<String, String> = mapOf(),
    val vpc_security_group_ids : List<String> = listOf(),
    val key_name : String = "\${var.key_name}",
    val count : Int,
    val ebs_block_device: InstanceEBSBlockDevice? = null,
    val ebs_optimized: Boolean = false,
    val associate_public_ip_address: Boolean = true,
//    @JsonIgnore
//    val subnets: Map<String, Subnet>,
    @JsonIgnore
    val subnet: Subnet,
    val subnet_id: String = subnet.id()
//    val subnet_id: String
) {

    init {
        if (ami == "") {
            ami = "\${data.aws_ami.image.id}"
        }
    }
}

data class InstanceEBSBlockDevice(
    val volume_type: String = "", // TODO (jwest): what default to use?
    val volume_size: Int = 256,
    val device_name: String = "/dev/xvdb", // TODO (jwest): probably not the right volume name to use
    val iops: Int = 0,
    val throughput: Int = 0,
    val delete_on_termination: Boolean = true,
    val encrypted: Boolean = false,
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
    val ingress: List<SecurityGroupRule>,
    val egress: List<SecurityGroupRule>,

    @JsonIgnore
    val vpc: VPC,
    val vpc_id: String = vpc.id()

) {
    fun id() = "\${aws_security_group.${name}.id}"
}

// Remove the default name and force it to be passed through
data class VPC(@JsonIgnore val name: String = "easy_cass_lab",
               var cidr_block : String = "10.0.0.0/16",
               var tags: Map<String, String>) {

    fun id() : String {
        return "\${aws_vpc.${name}.id}"
    }
}

/**
 * Internet Gateway
 * Attached to a VPC this allows internet connectivity.
 * https://docs.aws.amazon.com/vpc/latest/userguide/VPC_Internet_Gateway.html
 * https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/internet_gateway
 */
data class IGW(@JsonIgnore val vpc : VPC) {
    fun id(): String {
        return "\${aws_internet_gateway.${vpc.name}.id}"
    }

    val vpc_id : String = vpc.id()
    val tags = object {
        val Name = vpc.name
    }
}

/**
 * Each AZ is required to have its own subset with a block of IPs.
 * https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/subnet
 */
data class Subnet(@JsonIgnore val name: String,
                  @JsonIgnore var vpc: VPC,
                  var cidr_block: String,
                  var availability_zone: String,
                  var tags: Map<String, String>) {
    fun id(): String {
        return "\${aws_subnet.${name}.id}"
    }

    val vpc_id = vpc.id()
}

/**
 * Each Subnet has a NAT gateway, that allows outside access.
 * I'm not sure if I actually need this.
 * Https://docs.aws.amazon.com/vpc/latest/userguide/configure-subnets.html
 */
data class NATGateway(@JsonIgnore var subnet: Subnet, @JsonIgnore var igw: IGW) {
    var subnet_id = subnet.id()
    var depends_on = listOf("aws_internet_gateway.${igw.vpc.name}")

}

data class Route(val destination_cidr_block : String,
                 @JsonIgnore val gateway: IGW,
                 @JsonIgnore val routeTable: RouteTable,
                 val route_table_id : String = routeTable.id()
) {
    val gateway_id : String = gateway.id()
}

/**
 * Describes the allowed traffic between the internet gateway and the subnets
 */
data class RouteTable(
    @JsonIgnore val vpc: VPC,
    @JsonIgnore val name: String = vpc.name,
    @JsonIgnore val igw: IGW,
) {
    val vpc_id = vpc.id()

    fun id() : String = "\${aws_route_table.${name}.id}"
}

data class RouteTableAssociation(
    @JsonIgnore val subnet: Subnet,
    @JsonIgnore val routeTable: RouteTable,
    val subnet_id : String = subnet.id(),
    val route_table_id: String = routeTable.id()
)

data class AWSResource(
    var aws_vpc : Map<String, VPC>,
    var aws_internet_gateway : Map<String, IGW>,
    var aws_subnet: Map<String, Subnet>,
    var aws_instance : MutableMap<String, InstanceResource> = mutableMapOf(),
    var aws_security_group : MutableMap<String, SecurityGroupResource> = mutableMapOf(),
    var aws_route_table : Map<String, RouteTable>,
    var aws_route : Map<String, Route>,
    var aws_route_table_association: Map<String, RouteTableAssociation>
)