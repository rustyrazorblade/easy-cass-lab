package com.rustyrazorblade.easycasslab.configuration

import com.rustyrazorblade.easycasslab.providers.aws.InternetGatewayId
import com.rustyrazorblade.easycasslab.providers.aws.SecurityGroupId
import com.rustyrazorblade.easycasslab.providers.aws.SubnetId
import com.rustyrazorblade.easycasslab.providers.aws.VpcId

/**
 * Represents the core VPC infrastructure components.
 *
 * This data class holds the AWS resource IDs for a complete VPC setup
 * including networking and security components.
 *
 * @property vpcId The VPC ID
 * @property subnetId The subnet ID within the VPC
 * @property securityGroupId The security group ID for instance access
 * @property internetGatewayId The internet gateway ID for external connectivity
 */
data class VpcInfrastructure(
    val vpcId: VpcId,
    val subnetId: SubnetId,
    val securityGroupId: SecurityGroupId,
    val internetGatewayId: InternetGatewayId,
)
