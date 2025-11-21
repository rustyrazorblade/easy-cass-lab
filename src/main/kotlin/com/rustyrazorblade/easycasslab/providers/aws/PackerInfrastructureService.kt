package com.rustyrazorblade.easycasslab.providers.aws

import com.rustyrazorblade.easycasslab.Constants
import com.rustyrazorblade.easycasslab.configuration.VpcInfrastructure
import com.rustyrazorblade.easycasslab.output.OutputHandler
import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * Service for ensuring Packer AMI build infrastructure exists.
 *
 * This service orchestrates the creation of VPC infrastructure specifically
 * for Packer AMI builds. It uses the generic VpcService to create and manage
 * the required AWS resources with Packer-specific naming and configuration.
 *
 * The infrastructure created includes:
 * - VPC with CIDR 10.0.0.0/16
 * - Public subnet with CIDR 10.0.1.0/24
 * - Internet gateway for external connectivity
 * - Security group with SSH access (port 22)
 * - Route table with default route to internet gateway
 *
 * All resources are tagged with easy_cass_lab=1 to satisfy IAM policies.
 */
class PackerInfrastructureService(
    private val vpcService: VpcService,
    private val outputHandler: OutputHandler,
) {
    companion object {
        private val log = KotlinLogging.logger {}

        const val VPC_NAME = "easy-cass-lab-packer"
        const val SUBNET_NAME = "easy-cass-lab-packer-subnet"
        const val IGW_NAME = "easy-cass-lab-packer-igw"
        const val SG_NAME = "easy-cass-lab-packer-sg"
        const val VPC_CIDR = "10.0.0.0/16"
        const val SUBNET_CIDR = "10.0.1.0/24"
        const val TAG_KEY = "easy_cass_lab"
        const val TAG_VALUE = "1"
    }

    /**
     * Ensures all required Packer infrastructure exists and returns the details.
     *
     * This operation is idempotent - it will find existing resources or create
     * them if they don't exist. All resources are tagged with easy_cass_lab=1.
     *
     * @return VpcInfrastructure containing the IDs of all created/found resources
     */
    fun ensureInfrastructure(): VpcInfrastructure {
        log.info { "Ensuring Packer infrastructure exists..." }
        outputHandler.handleMessage("Ensuring Packer infrastructure exists...")

        val tags = mapOf(TAG_KEY to TAG_VALUE)

        // Create or find VPC
        val vpcId = vpcService.findOrCreateVpc(VPC_NAME, VPC_CIDR, tags)

        // Create or find internet gateway
        val igwId = vpcService.findOrCreateInternetGateway(vpcId, IGW_NAME, tags)

        // Create or find subnet
        val subnetId = vpcService.findOrCreateSubnet(vpcId, SUBNET_NAME, SUBNET_CIDR, tags)

        // Ensure routing is configured
        vpcService.ensureRouteTable(vpcId, subnetId, igwId)

        // Create or find security group
        val sgId =
            vpcService.findOrCreateSecurityGroup(
                vpcId,
                SG_NAME,
                "Security group for Packer AMI builds",
                tags,
            )

        // Ensure SSH access is configured
        vpcService.authorizeSecurityGroupIngress(sgId, Constants.Network.SSH_PORT, "0.0.0.0/0")

        val infrastructure = VpcInfrastructure(vpcId, subnetId, sgId, igwId)

        log.info { "Packer infrastructure ready: VPC=$vpcId, Subnet=$subnetId, SG=$sgId, IGW=$igwId" }
        outputHandler.handleMessage("Packer infrastructure ready")

        return infrastructure
    }
}
