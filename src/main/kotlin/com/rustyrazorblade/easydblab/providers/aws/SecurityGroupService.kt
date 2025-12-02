package com.rustyrazorblade.easydblab.providers.aws

import io.github.oshai.kotlinlogging.KotlinLogging
import software.amazon.awssdk.services.ec2.Ec2Client
import software.amazon.awssdk.services.ec2.model.DescribeSecurityGroupsRequest

/**
 * Represents a security group rule info (inbound or outbound) for display purposes.
 * Different from SecurityGroupRule which is used for creating rules.
 *
 * @property protocol Protocol (e.g., "TCP", "UDP", "All")
 * @property fromPort Start of port range (null for all traffic)
 * @property toPort End of port range (null for all traffic)
 * @property cidrBlocks List of CIDR blocks allowed
 * @property description Optional description of the rule
 */
data class SecurityGroupRuleInfo(
    val protocol: String,
    val fromPort: Int?,
    val toPort: Int?,
    val cidrBlocks: List<String>,
    val description: String?,
)

/**
 * Represents detailed information about a security group for status display
 *
 * @property securityGroupId The security group ID
 * @property name The security group name
 * @property description The security group description
 * @property vpcId The VPC ID the security group belongs to
 * @property inboundRules List of inbound (ingress) rules
 * @property outboundRules List of outbound (egress) rules
 */
data class SecurityGroupDetails(
    val securityGroupId: String,
    val name: String,
    val description: String,
    val vpcId: String,
    val inboundRules: List<SecurityGroupRuleInfo>,
    val outboundRules: List<SecurityGroupRuleInfo>,
)

/**
 * Service interface for security group operations.
 */
interface SecurityGroupService {
    /**
     * Get detailed information about a security group.
     *
     * @param securityGroupId The security group ID
     * @return SecurityGroupDetails or null if not found
     */
    fun describeSecurityGroup(securityGroupId: String): SecurityGroupDetails?
}

/**
 * AWS EC2 implementation of SecurityGroupService.
 */
class EC2SecurityGroupService(
    private val ec2Client: Ec2Client,
) : SecurityGroupService {
    companion object {
        private val log = KotlinLogging.logger {}
    }

    override fun describeSecurityGroup(securityGroupId: String): SecurityGroupDetails? {
        log.debug { "Describing security group: $securityGroupId" }

        val request =
            DescribeSecurityGroupsRequest
                .builder()
                .groupIds(securityGroupId)
                .build()

        val response =
            RetryUtil.withAwsRetry("describe-security-group") {
                ec2Client.describeSecurityGroups(request)
            }

        val sg = response.securityGroups().firstOrNull() ?: return null

        val inboundRules =
            sg.ipPermissions().map { permission ->
                SecurityGroupRuleInfo(
                    protocol = formatProtocol(permission.ipProtocol()),
                    fromPort = permission.fromPort(),
                    toPort = permission.toPort(),
                    cidrBlocks = permission.ipRanges().mapNotNull { it.cidrIp() },
                    description =
                        permission.ipRanges().firstOrNull()?.description()
                            ?: getPortDescription(permission.fromPort(), permission.toPort()),
                )
            }

        val outboundRules =
            sg.ipPermissionsEgress().map { permission ->
                SecurityGroupRuleInfo(
                    protocol = formatProtocol(permission.ipProtocol()),
                    fromPort = permission.fromPort(),
                    toPort = permission.toPort(),
                    cidrBlocks = permission.ipRanges().mapNotNull { it.cidrIp() },
                    description =
                        permission.ipRanges().firstOrNull()?.description()
                            ?: getPortDescription(permission.fromPort(), permission.toPort()),
                )
            }

        return SecurityGroupDetails(
            securityGroupId = sg.groupId(),
            name = sg.groupName(),
            description = sg.description(),
            vpcId = sg.vpcId(),
            inboundRules = inboundRules,
            outboundRules = outboundRules,
        )
    }

    /**
     * Format protocol for display
     */
    private fun formatProtocol(protocol: String): String =
        when (protocol) {
            "-1" -> "All"
            else -> protocol.uppercase()
        }

    /**
     * Get a human-readable description for common ports
     */
    @Suppress("MagicNumber", "CyclomaticComplexMethod")
    private fun getPortDescription(
        fromPort: Int?,
        toPort: Int?,
    ): String? {
        if (fromPort == null || toPort == null) return "All traffic"
        if (fromPort != toPort) return null

        return when (fromPort) {
            WellKnownPorts.SSH -> "SSH"
            WellKnownPorts.HTTP -> "HTTP"
            WellKnownPorts.HTTPS -> "HTTPS"
            WellKnownPorts.MYSQL -> "MySQL"
            WellKnownPorts.POSTGRESQL -> "PostgreSQL"
            WellKnownPorts.REDIS -> "Redis"
            WellKnownPorts.CASSANDRA_INTERNODE -> "Cassandra Inter-node"
            WellKnownPorts.CASSANDRA_INTERNODE_SSL -> "Cassandra Inter-node SSL"
            WellKnownPorts.CASSANDRA_CQL -> "Cassandra CQL"
            WellKnownPorts.CASSANDRA_CQL_SSL -> "Cassandra CQL SSL"
            WellKnownPorts.CASSANDRA_THRIFT -> "Cassandra Thrift"
            WellKnownPorts.CASSANDRA_JMX -> "Cassandra JMX"
            else -> null
        }
    }
}

/**
 * Well-known port numbers for common services
 */
private object WellKnownPorts {
    const val SSH = 22
    const val HTTP = 80
    const val HTTPS = 443
    const val MYSQL = 3306
    const val POSTGRESQL = 5432
    const val REDIS = 6379
    const val CASSANDRA_INTERNODE = 7000
    const val CASSANDRA_INTERNODE_SSL = 7001
    const val CASSANDRA_CQL = 9042
    const val CASSANDRA_CQL_SSL = 9142
    const val CASSANDRA_THRIFT = 9160
    const val CASSANDRA_JMX = 10000
}
