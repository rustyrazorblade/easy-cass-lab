package com.rustyrazorblade.easycasslab.providers.aws

/**
 * Service for managing AWS VPC infrastructure resources.
 *
 * This interface provides operations for creating and managing VPC components
 * including VPCs, subnets, internet gateways, security groups, and routing.
 * All operations are idempotent - they will find existing resources by name tag
 * or create them if they don't exist.
 */
interface VpcService {
    /**
     * Finds an existing VPC by name tag or creates a new one.
     *
     * @param name The name to use for the VPC (applied as Name tag)
     * @param cidr The CIDR block for the VPC (e.g., "10.0.0.0/16")
     * @param tags Additional tags to apply to the VPC
     * @return The VPC ID
     */
    fun findOrCreateVpc(
        name: ResourceName,
        cidr: Cidr,
        tags: Map<String, String>,
    ): VpcId

    /**
     * Finds an existing subnet by name tag or creates a new one.
     *
     * @param vpcId The VPC ID where the subnet should be created
     * @param name The name to use for the subnet (applied as Name tag)
     * @param cidr The CIDR block for the subnet (e.g., "10.0.1.0/24")
     * @param tags Additional tags to apply to the subnet
     * @return The subnet ID
     */
    fun findOrCreateSubnet(
        vpcId: VpcId,
        name: ResourceName,
        cidr: Cidr,
        tags: Map<String, String>,
    ): SubnetId

    /**
     * Finds an existing internet gateway by name tag or creates a new one,
     * and ensures it is attached to the specified VPC.
     *
     * @param vpcId The VPC ID to attach the internet gateway to
     * @param name The name to use for the internet gateway (applied as Name tag)
     * @param tags Additional tags to apply to the internet gateway
     * @return The internet gateway ID
     */
    fun findOrCreateInternetGateway(
        vpcId: VpcId,
        name: ResourceName,
        tags: Map<String, String>,
    ): InternetGatewayId

    /**
     * Finds an existing security group by name or creates a new one.
     *
     * @param vpcId The VPC ID where the security group should be created
     * @param name The name to use for the security group (applied as Name tag and group name)
     * @param description Description for the security group
     * @param tags Additional tags to apply to the security group
     * @return The security group ID
     */
    fun findOrCreateSecurityGroup(
        vpcId: VpcId,
        name: ResourceName,
        description: ResourceDescription,
        tags: Map<String, String>,
    ): SecurityGroupId

    /**
     * Ensures the route table for the subnet has a default route to the internet gateway.
     * Uses the VPC's main route table and associates it with the subnet.
     *
     * @param vpcId The VPC ID
     * @param subnetId The subnet ID to associate with the route table
     * @param igwId The internet gateway ID to route traffic to
     */
    fun ensureRouteTable(
        vpcId: VpcId,
        subnetId: SubnetId,
        igwId: InternetGatewayId,
    )

    /**
     * Adds an ingress rule to a security group if it doesn't already exist.
     *
     * @param securityGroupId The security group ID
     * @param port The port to allow (e.g., 22 for SSH)
     * @param cidr The CIDR block to allow traffic from (e.g., "0.0.0.0/0")
     */
    fun authorizeSecurityGroupIngress(
        securityGroupId: SecurityGroupId,
        port: Int,
        cidr: Cidr,
    )
}
