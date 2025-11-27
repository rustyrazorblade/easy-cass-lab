package com.rustyrazorblade.easycasslab.providers.aws

import com.rustyrazorblade.easycasslab.output.OutputHandler
import com.rustyrazorblade.easycasslab.providers.RetryUtil
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.resilience4j.retry.Retry
import software.amazon.awssdk.services.ec2.Ec2Client
import software.amazon.awssdk.services.ec2.model.AttachInternetGatewayRequest
import software.amazon.awssdk.services.ec2.model.AuthorizeSecurityGroupIngressRequest
import software.amazon.awssdk.services.ec2.model.CreateInternetGatewayRequest
import software.amazon.awssdk.services.ec2.model.CreateRouteRequest
import software.amazon.awssdk.services.ec2.model.CreateSecurityGroupRequest
import software.amazon.awssdk.services.ec2.model.CreateSubnetRequest
import software.amazon.awssdk.services.ec2.model.CreateTagsRequest
import software.amazon.awssdk.services.ec2.model.CreateVpcRequest
import software.amazon.awssdk.services.ec2.model.DescribeInternetGatewaysRequest
import software.amazon.awssdk.services.ec2.model.DescribeRouteTablesRequest
import software.amazon.awssdk.services.ec2.model.DescribeSecurityGroupsRequest
import software.amazon.awssdk.services.ec2.model.DescribeSubnetsRequest
import software.amazon.awssdk.services.ec2.model.DescribeVpcsRequest
import software.amazon.awssdk.services.ec2.model.Ec2Exception
import software.amazon.awssdk.services.ec2.model.Filter
import software.amazon.awssdk.services.ec2.model.IpPermission
import software.amazon.awssdk.services.ec2.model.IpRange
import software.amazon.awssdk.services.ec2.model.Tag

/**
 * Implementation of VpcService using AWS EC2 SDK.
 *
 * This service manages AWS VPC infrastructure with idempotent operations.
 * All resources are tagged with a Name tag and additional custom tags.
 * Operations use find-or-create pattern to ensure idempotency.
 */
class EC2VpcService(
    private val ec2Client: Ec2Client,
    private val outputHandler: OutputHandler,
) : VpcService {
    companion object {
        private val log = KotlinLogging.logger {}
    }

    /**
     * Executes an EC2 operation with retry logic for transient AWS failures.
     *
     * @param operationName Name of the operation for logging
     * @param operation The EC2 operation to execute
     * @return The result of the operation
     */
    private fun <T> withRetry(
        operationName: String,
        operation: () -> T,
    ): T {
        val retryConfig = RetryUtil.createAwsRetryConfig<T>()
        val retry = Retry.of(operationName, retryConfig)
        return Retry.decorateSupplier(retry, operation).get()
    }

    override fun findOrCreateVpc(
        name: ResourceName,
        cidr: Cidr,
        tags: Map<String, String>,
    ): VpcId {
        // Try to find existing VPC by Name tag
        val existingVpcId = findResourceByNameTag("vpc", name)
        if (existingVpcId != null) {
            log.info { "Found existing VPC: $name ($existingVpcId)" }
            outputHandler.handleMessage("Using existing VPC: $name")
            return existingVpcId
        }

        // Create new VPC
        log.info { "Creating VPC: $name with CIDR: $cidr" }
        outputHandler.handleMessage("Creating VPC: $name")

        val allTags = tags + ("Name" to name)
        val tagSpecification = buildTagSpecification("vpc", allTags)

        val createRequest =
            CreateVpcRequest
                .builder()
                .cidrBlock(cidr)
                .tagSpecifications(tagSpecification)
                .build()

        val createResponse = withRetry("create-vpc") { ec2Client.createVpc(createRequest) }
        val vpcId = createResponse.vpc().vpcId()

        log.info { "Created VPC: $name ($vpcId)" }
        return vpcId
    }

    override fun findOrCreateSubnet(
        vpcId: VpcId,
        name: ResourceName,
        cidr: Cidr,
        tags: Map<String, String>,
    ): SubnetId {
        // Try to find existing subnet by Name tag and VPC ID
        val existingSubnetId = findSubnetByNameAndVpc(name, vpcId)
        if (existingSubnetId != null) {
            log.info { "Found existing subnet: $name ($existingSubnetId)" }
            outputHandler.handleMessage("Using existing subnet: $name")
            // Ensure auto-assign public IP is enabled (idempotent operation)
            enableAutoAssignPublicIp(existingSubnetId)
            return existingSubnetId
        }

        // Create new subnet
        log.info { "Creating subnet: $name in VPC: $vpcId with CIDR: $cidr" }
        outputHandler.handleMessage("Creating subnet: $name")

        val allTags = tags + ("Name" to name)
        log.debug { "Subnet tags being applied: $allTags" }
        val tagSpecification = buildTagSpecification("subnet", allTags)

        val createRequest =
            CreateSubnetRequest
                .builder()
                .vpcId(vpcId)
                .cidrBlock(cidr)
                .tagSpecifications(tagSpecification)
                .build()

        log.debug { "CreateSubnet request: vpcId=$vpcId, cidr=$cidr, tags=${tagSpecification.tags()}" }
        val createResponse = withRetry("create-subnet") { ec2Client.createSubnet(createRequest) }
        val subnetId = createResponse.subnet().subnetId()

        // Enable auto-assign public IP for instances launched in this subnet
        enableAutoAssignPublicIp(subnetId)

        log.info { "Created subnet: $name ($subnetId)" }
        return subnetId
    }

    override fun findOrCreateInternetGateway(
        vpcId: VpcId,
        name: ResourceName,
        tags: Map<String, String>,
    ): InternetGatewayId {
        // Try to find existing internet gateway by Name tag
        val existingIgwId = findInternetGatewayByNameAndVpc(name, vpcId)
        if (existingIgwId != null) {
            log.info { "Found existing internet gateway: $name ($existingIgwId)" }
            outputHandler.handleMessage("Using existing internet gateway: $name")
            return existingIgwId
        }

        // Create new internet gateway
        log.info { "Creating internet gateway: $name" }
        outputHandler.handleMessage("Creating internet gateway: $name")

        val allTags = tags + ("Name" to name)
        val tagSpecification = buildTagSpecification("internet-gateway", allTags)

        val createRequest =
            CreateInternetGatewayRequest
                .builder()
                .tagSpecifications(tagSpecification)
                .build()

        val createResponse = withRetry("create-igw") { ec2Client.createInternetGateway(createRequest) }
        val igwId = createResponse.internetGateway().internetGatewayId()

        // Attach to VPC
        val attachRequest =
            AttachInternetGatewayRequest
                .builder()
                .internetGatewayId(igwId)
                .vpcId(vpcId)
                .build()

        withRetry("attach-igw") { ec2Client.attachInternetGateway(attachRequest) }
        log.info { "Created and attached internet gateway: $name ($igwId) to VPC: $vpcId" }

        return igwId
    }

    override fun findOrCreateSecurityGroup(
        vpcId: VpcId,
        name: ResourceName,
        description: ResourceDescription,
        tags: Map<String, String>,
    ): SecurityGroupId {
        // Try to find existing security group by name and VPC ID
        val existingSgId = findSecurityGroupByNameAndVpc(name, vpcId)
        if (existingSgId != null) {
            log.info { "Found existing security group: $name ($existingSgId)" }
            outputHandler.handleMessage("Using existing security group: $name")
            return existingSgId
        }

        // Create new security group
        log.info { "Creating security group: $name in VPC: $vpcId" }
        outputHandler.handleMessage("Creating security group: $name")

        val allTags = tags + ("Name" to name)
        val tagSpecification = buildTagSpecification("security-group", allTags)

        val createRequest =
            CreateSecurityGroupRequest
                .builder()
                .groupName(name)
                .description(description)
                .vpcId(vpcId)
                .tagSpecifications(tagSpecification)
                .build()

        val createResponse = withRetry("create-sg") { ec2Client.createSecurityGroup(createRequest) }
        val sgId = createResponse.groupId()

        log.info { "Created security group: $name ($sgId)" }
        return sgId
    }

    override fun ensureRouteTable(
        vpcId: VpcId,
        subnetId: SubnetId,
        igwId: InternetGatewayId,
    ) {
        // Get the main route table for the VPC
        val describeRequest =
            DescribeRouteTablesRequest
                .builder()
                .filters(
                    Filter
                        .builder()
                        .name("vpc-id")
                        .values(vpcId)
                        .build(),
                    Filter
                        .builder()
                        .name("association.main")
                        .values("true")
                        .build(),
                ).build()

        val routeTables = ec2Client.describeRouteTables(describeRequest).routeTables()
        if (routeTables.isEmpty()) {
            log.error { "No main route table found for VPC: $vpcId" }
            error("No main route table found for VPC: $vpcId")
        }

        val routeTableId = routeTables[0].routeTableId()

        // Check if default route already exists
        val existingRoute =
            routeTables[0].routes().any { route ->
                route.destinationCidrBlock() == "0.0.0.0/0" && route.gatewayId() == igwId
            }

        if (existingRoute) {
            log.info { "Default route already exists in route table: $routeTableId" }
            return
        }

        // Create default route to internet gateway
        try {
            val createRouteRequest =
                CreateRouteRequest
                    .builder()
                    .routeTableId(routeTableId)
                    .destinationCidrBlock("0.0.0.0/0")
                    .gatewayId(igwId)
                    .build()

            ec2Client.createRoute(createRouteRequest)
            log.info { "Created default route to internet gateway in route table: $routeTableId" }
            outputHandler.handleMessage("Configured routing to internet gateway")
        } catch (e: Ec2Exception) {
            if (e.awsErrorDetails()?.errorCode() == "RouteAlreadyExists") {
                log.info { "Route already exists, continuing" }
            } else {
                throw e
            }
        }
    }

    override fun authorizeSecurityGroupIngress(
        securityGroupId: SecurityGroupId,
        port: Int,
        cidr: Cidr,
    ) {
        // Check if rule already exists
        val describeRequest =
            DescribeSecurityGroupsRequest
                .builder()
                .groupIds(securityGroupId)
                .build()

        val securityGroups = ec2Client.describeSecurityGroups(describeRequest).securityGroups()
        if (securityGroups.isEmpty()) {
            log.error { "Security group not found: $securityGroupId" }
            throw IllegalArgumentException("Security group not found: $securityGroupId")
        }

        val existingRule =
            securityGroups[0].ipPermissions().any { permission ->
                permission.fromPort() == port &&
                    permission.toPort() == port &&
                    permission.ipRanges().any { it.cidrIp() == cidr }
            }

        if (existingRule) {
            log.info { "Ingress rule already exists for port $port from $cidr" }
            return
        }

        // Add ingress rule
        try {
            val ipPermission =
                IpPermission
                    .builder()
                    .ipProtocol("tcp")
                    .fromPort(port)
                    .toPort(port)
                    .ipRanges(
                        IpRange
                            .builder()
                            .cidrIp(cidr)
                            .build(),
                    ).build()

            val authorizeRequest =
                AuthorizeSecurityGroupIngressRequest
                    .builder()
                    .groupId(securityGroupId)
                    .ipPermissions(ipPermission)
                    .build()

            ec2Client.authorizeSecurityGroupIngress(authorizeRequest)
            log.info { "Added ingress rule for port $port from $cidr" }
            outputHandler.handleMessage("Configured security group ingress rule for SSH (port $port)")
        } catch (e: Ec2Exception) {
            if (e.awsErrorDetails()?.errorCode() == "InvalidPermission.Duplicate") {
                log.info { "Ingress rule already exists, continuing" }
            } else {
                throw e
            }
        }
    }

    private fun findResourceByNameTag(
        resourceType: String,
        name: ResourceName,
    ): VpcId? {
        val describeRequest =
            DescribeVpcsRequest
                .builder()
                .filters(
                    Filter
                        .builder()
                        .name("tag:Name")
                        .values(name)
                        .build(),
                ).build()

        val vpcs = ec2Client.describeVpcs(describeRequest).vpcs()
        return vpcs.firstOrNull()?.vpcId()
    }

    private fun findSubnetByNameAndVpc(
        name: ResourceName,
        vpcId: VpcId,
    ): SubnetId? {
        val describeRequest =
            DescribeSubnetsRequest
                .builder()
                .filters(
                    Filter
                        .builder()
                        .name("tag:Name")
                        .values(name)
                        .build(),
                    Filter
                        .builder()
                        .name("vpc-id")
                        .values(vpcId)
                        .build(),
                ).build()

        val subnets = ec2Client.describeSubnets(describeRequest).subnets()
        return subnets.firstOrNull()?.subnetId()
    }

    private fun findInternetGatewayByNameAndVpc(
        name: ResourceName,
        vpcId: VpcId,
    ): InternetGatewayId? {
        val describeRequest =
            DescribeInternetGatewaysRequest
                .builder()
                .filters(
                    Filter
                        .builder()
                        .name("tag:Name")
                        .values(name)
                        .build(),
                    Filter
                        .builder()
                        .name("attachment.vpc-id")
                        .values(vpcId)
                        .build(),
                ).build()

        val igws = ec2Client.describeInternetGateways(describeRequest).internetGateways()
        return igws.firstOrNull()?.internetGatewayId()
    }

    private fun findSecurityGroupByNameAndVpc(
        name: ResourceName,
        vpcId: VpcId,
    ): SecurityGroupId? {
        val describeRequest =
            DescribeSecurityGroupsRequest
                .builder()
                .filters(
                    Filter
                        .builder()
                        .name("group-name")
                        .values(name)
                        .build(),
                    Filter
                        .builder()
                        .name("vpc-id")
                        .values(vpcId)
                        .build(),
                ).build()

        val sgs = ec2Client.describeSecurityGroups(describeRequest).securityGroups()
        return sgs.firstOrNull()?.groupId()
    }

    private fun createTags(
        resourceId: String,
        tags: Map<String, String>,
    ) {
        val awsTags =
            tags.map { (key, value) ->
                Tag
                    .builder()
                    .key(key)
                    .value(value)
                    .build()
            }

        val createTagsRequest =
            CreateTagsRequest
                .builder()
                .resources(resourceId)
                .tags(awsTags)
                .build()

        ec2Client.createTags(createTagsRequest)
    }

    private fun buildTagSpecification(
        resourceType: String,
        tags: Map<String, String>,
    ): software.amazon.awssdk.services.ec2.model.TagSpecification {
        val awsTags =
            tags.map { (key, value) ->
                Tag
                    .builder()
                    .key(key)
                    .value(value)
                    .build()
            }

        return software.amazon.awssdk.services.ec2.model.TagSpecification
            .builder()
            .resourceType(resourceType)
            .tags(awsTags)
            .build()
    }

    private fun enableAutoAssignPublicIp(subnetId: SubnetId) {
        log.info { "Enabling auto-assign public IP for subnet: $subnetId" }

        val modifyRequest =
            software.amazon.awssdk.services.ec2.model.ModifySubnetAttributeRequest
                .builder()
                .subnetId(subnetId)
                .mapPublicIpOnLaunch(
                    software.amazon.awssdk.services.ec2.model.AttributeBooleanValue
                        .builder()
                        .value(true)
                        .build(),
                ).build()

        ec2Client.modifySubnetAttribute(modifyRequest)
        log.info { "Auto-assign public IP enabled for subnet: $subnetId" }
    }
}
