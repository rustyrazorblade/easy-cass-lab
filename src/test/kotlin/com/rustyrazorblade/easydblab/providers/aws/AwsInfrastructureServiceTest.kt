package com.rustyrazorblade.easydblab.providers.aws

import com.rustyrazorblade.easydblab.Constants
import com.rustyrazorblade.easydblab.output.OutputHandler
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

internal class AwsInfrastructureServiceTest {
    private val mockVpcService: VpcService = mock()
    private val mockOutputHandler: OutputHandler = mock()
    private val awsInfraService = AwsInfrastructureService(mockVpcService, mockOutputHandler)

    @Test
    fun `ensurePackerInfrastructure should create all required resources in correct order`() {
        // Mock VPC creation
        whenever(
            mockVpcService.createVpc(
                eq("easy-db-lab-packer"),
                eq("10.0.0.0/16"),
                eq(mapOf("easy_cass_lab" to "1")),
            ),
        ).thenReturn("vpc-12345")

        // Mock Internet Gateway creation
        whenever(
            mockVpcService.findOrCreateInternetGateway(
                eq("vpc-12345"),
                eq("easy-db-lab-packer-igw"),
                eq(mapOf("easy_cass_lab" to "1")),
            ),
        ).thenReturn("igw-12345")

        // Mock Subnet creation
        whenever(
            mockVpcService.findOrCreateSubnet(
                eq("vpc-12345"),
                eq("easy-db-lab-packer-subnet"),
                eq("10.0.1.0/24"),
                eq(mapOf("easy_cass_lab" to "1")),
                anyOrNull(),
            ),
        ).thenReturn("subnet-12345")

        // Mock Security Group creation
        whenever(
            mockVpcService.findOrCreateSecurityGroup(
                eq("vpc-12345"),
                eq("easy-db-lab-packer-sg"),
                eq("Security group for Packer AMI builds"),
                eq(mapOf("easy_cass_lab" to "1")),
            ),
        ).thenReturn("sg-12345")

        val result = awsInfraService.ensurePackerInfrastructure(Constants.Network.SSH_PORT)

        assertThat(result.vpcId).isEqualTo("vpc-12345")
        assertThat(result.subnetId).isEqualTo("subnet-12345")
        assertThat(result.securityGroupId).isEqualTo("sg-12345")
        assertThat(result.internetGatewayId).isEqualTo("igw-12345")
    }

    @Test
    fun `ensurePackerInfrastructure should call VpcService methods in correct order`() {
        whenever(mockVpcService.createVpc(any(), any(), any())).thenReturn("vpc-12345")
        whenever(mockVpcService.findOrCreateInternetGateway(any(), any(), any())).thenReturn("igw-12345")
        whenever(mockVpcService.findOrCreateSubnet(any(), any(), any(), any(), anyOrNull())).thenReturn("subnet-12345")
        whenever(mockVpcService.findOrCreateSecurityGroup(any(), any(), any(), any())).thenReturn("sg-12345")

        awsInfraService.ensurePackerInfrastructure(Constants.Network.SSH_PORT)

        val inOrder = inOrder(mockVpcService)

        // VPC must be created first
        inOrder.verify(mockVpcService).createVpc(any(), any(), any())

        // Internet gateway must be created after VPC
        inOrder.verify(mockVpcService).findOrCreateInternetGateway(any(), any(), any())

        // Subnet must be created after VPC
        inOrder.verify(mockVpcService).findOrCreateSubnet(any(), any(), any(), any(), anyOrNull())

        // Route table must be configured after VPC, subnet, and IGW exist
        inOrder.verify(mockVpcService).ensureRouteTable(any(), any(), any(), any())

        // Security group must be created after VPC
        inOrder.verify(mockVpcService).findOrCreateSecurityGroup(any(), any(), any(), any())

        // Security group ingress must be configured after security group exists
        inOrder.verify(mockVpcService).authorizeSecurityGroupIngress(any(), any(), any(), any(), any())
    }

    @Test
    fun `ensurePackerInfrastructure should configure route table with correct VPC, subnet, and IGW IDs`() {
        whenever(mockVpcService.createVpc(any(), any(), any())).thenReturn("vpc-12345")
        whenever(mockVpcService.findOrCreateInternetGateway(any(), any(), any())).thenReturn("igw-12345")
        whenever(mockVpcService.findOrCreateSubnet(any(), any(), any(), any(), anyOrNull())).thenReturn("subnet-12345")
        whenever(mockVpcService.findOrCreateSecurityGroup(any(), any(), any(), any())).thenReturn("sg-12345")

        awsInfraService.ensurePackerInfrastructure(Constants.Network.SSH_PORT)

        val inOrder = inOrder(mockVpcService)
        inOrder.verify(mockVpcService).ensureRouteTable(
            eq("vpc-12345"),
            eq("subnet-12345"),
            eq("igw-12345"),
            eq(mapOf("easy_cass_lab" to "1")),
        )
    }

    @Test
    fun `ensurePackerInfrastructure should configure security group ingress for SSH`() {
        whenever(mockVpcService.createVpc(any(), any(), any())).thenReturn("vpc-12345")
        whenever(mockVpcService.findOrCreateInternetGateway(any(), any(), any())).thenReturn("igw-12345")
        whenever(mockVpcService.findOrCreateSubnet(any(), any(), any(), any(), anyOrNull())).thenReturn("subnet-12345")
        whenever(mockVpcService.findOrCreateSecurityGroup(any(), any(), any(), any())).thenReturn("sg-12345")

        awsInfraService.ensurePackerInfrastructure(Constants.Network.SSH_PORT)

        val inOrder = inOrder(mockVpcService)
        inOrder.verify(mockVpcService).authorizeSecurityGroupIngress(
            eq("sg-12345"),
            eq(Constants.Network.SSH_PORT),
            eq(Constants.Network.SSH_PORT),
            eq("0.0.0.0/0"),
            eq("tcp"),
        )
    }

    @Test
    fun `ensurePackerInfrastructure should use correct resource names`() {
        whenever(mockVpcService.createVpc(any(), any(), any())).thenReturn("vpc-12345")
        whenever(mockVpcService.findOrCreateInternetGateway(any(), any(), any())).thenReturn("igw-12345")
        whenever(mockVpcService.findOrCreateSubnet(any(), any(), any(), any(), anyOrNull())).thenReturn("subnet-12345")
        whenever(mockVpcService.findOrCreateSecurityGroup(any(), any(), any(), any())).thenReturn("sg-12345")

        awsInfraService.ensurePackerInfrastructure(Constants.Network.SSH_PORT)

        val inOrder = inOrder(mockVpcService)

        inOrder.verify(mockVpcService).createVpc(
            eq("easy-db-lab-packer"),
            any(),
            any(),
        )

        inOrder.verify(mockVpcService).findOrCreateInternetGateway(
            any(),
            eq("easy-db-lab-packer-igw"),
            any(),
        )

        inOrder.verify(mockVpcService).findOrCreateSubnet(
            any(),
            eq("easy-db-lab-packer-subnet"),
            any(),
            any(),
            anyOrNull(),
        )

        inOrder.verify(mockVpcService).findOrCreateSecurityGroup(
            any(),
            eq("easy-db-lab-packer-sg"),
            any(),
            any(),
        )
    }

    @Test
    fun `ensurePackerInfrastructure should use correct CIDR blocks`() {
        whenever(mockVpcService.createVpc(any(), any(), any())).thenReturn("vpc-12345")
        whenever(mockVpcService.findOrCreateInternetGateway(any(), any(), any())).thenReturn("igw-12345")
        whenever(mockVpcService.findOrCreateSubnet(any(), any(), any(), any(), anyOrNull())).thenReturn("subnet-12345")
        whenever(mockVpcService.findOrCreateSecurityGroup(any(), any(), any(), any())).thenReturn("sg-12345")

        awsInfraService.ensurePackerInfrastructure(Constants.Network.SSH_PORT)

        val inOrder = inOrder(mockVpcService)

        inOrder.verify(mockVpcService).createVpc(
            any(),
            eq("10.0.0.0/16"),
            any(),
        )

        inOrder.verify(mockVpcService).findOrCreateSubnet(
            any(),
            any(),
            eq("10.0.1.0/24"),
            any(),
            anyOrNull(),
        )
    }

    @Test
    fun `ensurePackerInfrastructure should tag all resources with easy_cass_lab tag`() {
        whenever(mockVpcService.createVpc(any(), any(), any())).thenReturn("vpc-12345")
        whenever(mockVpcService.findOrCreateInternetGateway(any(), any(), any())).thenReturn("igw-12345")
        whenever(mockVpcService.findOrCreateSubnet(any(), any(), any(), any(), anyOrNull())).thenReturn("subnet-12345")
        whenever(mockVpcService.findOrCreateSecurityGroup(any(), any(), any(), any())).thenReturn("sg-12345")

        awsInfraService.ensurePackerInfrastructure(Constants.Network.SSH_PORT)

        val expectedTags = mapOf("easy_cass_lab" to "1")

        val inOrder = inOrder(mockVpcService)

        inOrder.verify(mockVpcService).createVpc(
            any(),
            any(),
            eq(expectedTags),
        )

        inOrder.verify(mockVpcService).findOrCreateInternetGateway(
            any(),
            any(),
            eq(expectedTags),
        )

        inOrder.verify(mockVpcService).findOrCreateSubnet(
            any(),
            any(),
            any(),
            eq(expectedTags),
            anyOrNull(),
        )

        inOrder.verify(mockVpcService).findOrCreateSecurityGroup(
            any(),
            any(),
            any(),
            eq(expectedTags),
        )
    }

    @Test
    fun `ensurePackerInfrastructure should be idempotent`() {
        whenever(mockVpcService.createVpc(any(), any(), any())).thenReturn("vpc-12345")
        whenever(mockVpcService.findOrCreateInternetGateway(any(), any(), any())).thenReturn("igw-12345")
        whenever(mockVpcService.findOrCreateSubnet(any(), any(), any(), any(), anyOrNull())).thenReturn("subnet-12345")
        whenever(mockVpcService.findOrCreateSecurityGroup(any(), any(), any(), any())).thenReturn("sg-12345")

        // Call twice - should produce same result
        val result1 = awsInfraService.ensurePackerInfrastructure(Constants.Network.SSH_PORT)
        val result2 = awsInfraService.ensurePackerInfrastructure(Constants.Network.SSH_PORT)

        assertThat(result1).isEqualTo(result2)
    }

    // Tests for setupVpcNetworking

    @Test
    fun `setupVpcNetworking should create subnets for each availability zone`() {
        whenever(mockVpcService.findOrCreateSubnet(any(), any(), any(), any(), anyOrNull()))
            .thenReturn("subnet-1", "subnet-2", "subnet-3")
        whenever(mockVpcService.findOrCreateInternetGateway(any(), any(), any())).thenReturn("igw-12345")
        whenever(mockVpcService.findOrCreateSecurityGroup(any(), any(), any(), any())).thenReturn("sg-12345")

        val config =
            VpcNetworkingConfig(
                vpcId = "vpc-existing",
                clusterName = "test-cluster",
                clusterId = "cluster-123",
                region = "us-east-1",
                availabilityZones = listOf("a", "b", "c"),
                isOpen = true,
            )

        val result = awsInfraService.setupVpcNetworking(config) { "192.168.1.1" }

        assertThat(result.subnetIds).hasSize(3)
        assertThat(result.subnetIds).containsExactly("subnet-1", "subnet-2", "subnet-3")
    }

    @Test
    fun `setupVpcNetworking should create subnets with correct availability zone names`() {
        whenever(mockVpcService.findOrCreateSubnet(any(), any(), any(), any(), anyOrNull())).thenReturn("subnet-1")
        whenever(mockVpcService.findOrCreateInternetGateway(any(), any(), any())).thenReturn("igw-12345")
        whenever(mockVpcService.findOrCreateSecurityGroup(any(), any(), any(), any())).thenReturn("sg-12345")

        val config =
            VpcNetworkingConfig(
                vpcId = "vpc-existing",
                clusterName = "test-cluster",
                clusterId = "cluster-123",
                region = "us-west-2",
                availabilityZones = listOf("a", "b"),
                isOpen = true,
            )

        awsInfraService.setupVpcNetworking(config) { "192.168.1.1" }

        val inOrder = inOrder(mockVpcService)
        inOrder.verify(mockVpcService).findOrCreateSubnet(
            eq("vpc-existing"),
            eq("test-cluster-subnet-0"),
            any(),
            any(),
            eq("us-west-2a"),
        )
        inOrder.verify(mockVpcService).findOrCreateSubnet(
            eq("vpc-existing"),
            eq("test-cluster-subnet-1"),
            any(),
            any(),
            eq("us-west-2b"),
        )
    }

    @Test
    fun `setupVpcNetworking should create internet gateway`() {
        whenever(mockVpcService.findOrCreateSubnet(any(), any(), any(), any(), anyOrNull())).thenReturn("subnet-1")
        whenever(mockVpcService.findOrCreateInternetGateway(any(), any(), any())).thenReturn("igw-test")
        whenever(mockVpcService.findOrCreateSecurityGroup(any(), any(), any(), any())).thenReturn("sg-12345")

        val config =
            VpcNetworkingConfig(
                vpcId = "vpc-existing",
                clusterName = "my-cluster",
                clusterId = "cluster-123",
                region = "us-east-1",
                availabilityZones = listOf("a"),
                isOpen = true,
            )

        val result = awsInfraService.setupVpcNetworking(config) { "192.168.1.1" }

        assertThat(result.internetGatewayId).isEqualTo("igw-test")
        inOrder(mockVpcService).verify(mockVpcService).findOrCreateInternetGateway(
            eq("vpc-existing"),
            eq("my-cluster-igw"),
            any(),
        )
    }

    @Test
    fun `setupVpcNetworking should configure route tables for each subnet`() {
        whenever(mockVpcService.findOrCreateSubnet(any(), any(), any(), any(), anyOrNull()))
            .thenReturn("subnet-1", "subnet-2")
        whenever(mockVpcService.findOrCreateInternetGateway(any(), any(), any())).thenReturn("igw-12345")
        whenever(mockVpcService.findOrCreateSecurityGroup(any(), any(), any(), any())).thenReturn("sg-12345")

        val config =
            VpcNetworkingConfig(
                vpcId = "vpc-existing",
                clusterName = "test-cluster",
                clusterId = "cluster-123",
                region = "us-east-1",
                availabilityZones = listOf("a", "b"),
                isOpen = true,
            )

        awsInfraService.setupVpcNetworking(config) { "192.168.1.1" }

        val expectedTags =
            mapOf(
                "easy_cass_lab" to "1",
                "ClusterId" to "cluster-123",
            )
        val inOrder = inOrder(mockVpcService)
        inOrder.verify(mockVpcService).ensureRouteTable("vpc-existing", "subnet-1", "igw-12345", expectedTags)
        inOrder.verify(mockVpcService).ensureRouteTable("vpc-existing", "subnet-2", "igw-12345", expectedTags)
    }

    @Test
    fun `setupVpcNetworking should create security group with correct name`() {
        whenever(mockVpcService.findOrCreateSubnet(any(), any(), any(), any(), anyOrNull())).thenReturn("subnet-1")
        whenever(mockVpcService.findOrCreateInternetGateway(any(), any(), any())).thenReturn("igw-12345")
        whenever(mockVpcService.findOrCreateSecurityGroup(any(), any(), any(), any())).thenReturn("sg-test")

        val config =
            VpcNetworkingConfig(
                vpcId = "vpc-existing",
                clusterName = "prod-cluster",
                clusterId = "cluster-123",
                region = "us-east-1",
                availabilityZones = listOf("a"),
                isOpen = true,
            )

        val result = awsInfraService.setupVpcNetworking(config) { "192.168.1.1" }

        assertThat(result.securityGroupId).isEqualTo("sg-test")
        inOrder(mockVpcService).verify(mockVpcService).findOrCreateSecurityGroup(
            eq("vpc-existing"),
            eq("prod-cluster-sg"),
            eq("Security group for easy-db-lab cluster prod-cluster"),
            any(),
        )
    }

    @Test
    fun `setupVpcNetworking should use 0_0_0_0_0 for SSH when open is true`() {
        whenever(mockVpcService.findOrCreateSubnet(any(), any(), any(), any(), anyOrNull())).thenReturn("subnet-1")
        whenever(mockVpcService.findOrCreateInternetGateway(any(), any(), any())).thenReturn("igw-12345")
        whenever(mockVpcService.findOrCreateSecurityGroup(any(), any(), any(), any())).thenReturn("sg-12345")

        val config =
            VpcNetworkingConfig(
                vpcId = "vpc-existing",
                clusterName = "test-cluster",
                clusterId = "cluster-123",
                region = "us-east-1",
                availabilityZones = listOf("a"),
                isOpen = true,
            )

        awsInfraService.setupVpcNetworking(config) { "192.168.1.1" }

        inOrder(mockVpcService).verify(mockVpcService).authorizeSecurityGroupIngress(
            eq("sg-12345"),
            eq(Constants.Network.SSH_PORT),
            eq(Constants.Network.SSH_PORT),
            eq("0.0.0.0/0"),
            eq("tcp"),
        )
    }

    @Test
    fun `setupVpcNetworking should use external IP for SSH when open is false`() {
        whenever(mockVpcService.findOrCreateSubnet(any(), any(), any(), any(), anyOrNull())).thenReturn("subnet-1")
        whenever(mockVpcService.findOrCreateInternetGateway(any(), any(), any())).thenReturn("igw-12345")
        whenever(mockVpcService.findOrCreateSecurityGroup(any(), any(), any(), any())).thenReturn("sg-12345")

        val config =
            VpcNetworkingConfig(
                vpcId = "vpc-existing",
                clusterName = "test-cluster",
                clusterId = "cluster-123",
                region = "us-east-1",
                availabilityZones = listOf("a"),
                isOpen = false,
            )

        awsInfraService.setupVpcNetworking(config) { "203.0.113.50" }

        inOrder(mockVpcService).verify(mockVpcService).authorizeSecurityGroupIngress(
            eq("sg-12345"),
            eq(Constants.Network.SSH_PORT),
            eq(Constants.Network.SSH_PORT),
            eq("203.0.113.50/32"),
            eq("tcp"),
        )
    }

    @Test
    fun `setupVpcNetworking should allow VPC internal traffic for TCP and UDP`() {
        whenever(mockVpcService.findOrCreateSubnet(any(), any(), any(), any(), anyOrNull())).thenReturn("subnet-1")
        whenever(mockVpcService.findOrCreateInternetGateway(any(), any(), any())).thenReturn("igw-12345")
        whenever(mockVpcService.findOrCreateSecurityGroup(any(), any(), any(), any())).thenReturn("sg-12345")

        val config =
            VpcNetworkingConfig(
                vpcId = "vpc-existing",
                clusterName = "test-cluster",
                clusterId = "cluster-123",
                region = "us-east-1",
                availabilityZones = listOf("a"),
                isOpen = true,
            )

        awsInfraService.setupVpcNetworking(config) { "192.168.1.1" }

        val inOrder = inOrder(mockVpcService)
        // TCP internal traffic
        inOrder.verify(mockVpcService).authorizeSecurityGroupIngress(
            eq("sg-12345"),
            eq(Constants.Network.MIN_PORT),
            eq(Constants.Network.MAX_PORT),
            eq(Constants.Vpc.DEFAULT_CIDR),
            eq("tcp"),
        )
        // UDP internal traffic
        inOrder.verify(mockVpcService).authorizeSecurityGroupIngress(
            eq("sg-12345"),
            eq(Constants.Network.MIN_PORT),
            eq(Constants.Network.MAX_PORT),
            eq(Constants.Vpc.DEFAULT_CIDR),
            eq("udp"),
        )
    }

    @Test
    fun `setupVpcNetworking should include tags with ClusterId`() {
        whenever(mockVpcService.findOrCreateSubnet(any(), any(), any(), any(), anyOrNull())).thenReturn("subnet-1")
        whenever(mockVpcService.findOrCreateInternetGateway(any(), any(), any())).thenReturn("igw-12345")
        whenever(mockVpcService.findOrCreateSecurityGroup(any(), any(), any(), any())).thenReturn("sg-12345")

        val config =
            VpcNetworkingConfig(
                vpcId = "vpc-existing",
                clusterName = "test-cluster",
                clusterId = "unique-cluster-id",
                region = "us-east-1",
                availabilityZones = listOf("a"),
                isOpen = true,
                tags = mapOf("Environment" to "test"),
            )

        awsInfraService.setupVpcNetworking(config) { "192.168.1.1" }

        val expectedTags =
            mapOf(
                "Environment" to "test",
                "easy_cass_lab" to "1",
                "ClusterId" to "unique-cluster-id",
            )

        inOrder(mockVpcService).verify(mockVpcService).findOrCreateSubnet(
            any(),
            any(),
            any(),
            eq(expectedTags),
            any(),
        )
    }

    @Test
    fun `setupVpcNetworking should return VpcInfrastructure with correct vpcId`() {
        whenever(mockVpcService.findOrCreateSubnet(any(), any(), any(), any(), anyOrNull())).thenReturn("subnet-1")
        whenever(mockVpcService.findOrCreateInternetGateway(any(), any(), any())).thenReturn("igw-12345")
        whenever(mockVpcService.findOrCreateSecurityGroup(any(), any(), any(), any())).thenReturn("sg-12345")

        val config =
            VpcNetworkingConfig(
                vpcId = "vpc-my-existing-vpc",
                clusterName = "test-cluster",
                clusterId = "cluster-123",
                region = "us-east-1",
                availabilityZones = listOf("a"),
                isOpen = true,
            )

        val result = awsInfraService.setupVpcNetworking(config) { "192.168.1.1" }

        assertThat(result.vpcId).isEqualTo("vpc-my-existing-vpc")
    }
}
