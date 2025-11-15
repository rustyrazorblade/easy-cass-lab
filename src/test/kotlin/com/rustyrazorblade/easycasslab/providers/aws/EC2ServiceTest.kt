package com.rustyrazorblade.easycasslab.providers.aws

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import software.amazon.awssdk.services.ec2.Ec2Client
import software.amazon.awssdk.services.ec2.model.BlockDeviceMapping
import software.amazon.awssdk.services.ec2.model.DeleteSnapshotRequest
import software.amazon.awssdk.services.ec2.model.DeleteSnapshotResponse
import software.amazon.awssdk.services.ec2.model.DeregisterImageRequest
import software.amazon.awssdk.services.ec2.model.DeregisterImageResponse
import software.amazon.awssdk.services.ec2.model.DescribeImagesRequest
import software.amazon.awssdk.services.ec2.model.DescribeImagesResponse
import software.amazon.awssdk.services.ec2.model.DescribeSnapshotsRequest
import software.amazon.awssdk.services.ec2.model.DescribeSnapshotsResponse
import software.amazon.awssdk.services.ec2.model.EbsBlockDevice
import software.amazon.awssdk.services.ec2.model.Image
import software.amazon.awssdk.services.ec2.model.Snapshot
import java.time.Instant

internal class EC2ServiceTest {
    private val mockEc2Client: Ec2Client = mock()
    private val ec2Service = EC2Service(mockEc2Client)

    @Test
    fun `listPrivateAMIs should filter by owner and name pattern`() {
        val testImage =
            Image
                .builder()
                .imageId("ami-111")
                .name("rustyrazorblade/images/easy-cass-lab-cassandra-amd64-20240101")
                .architecture("x86_64")
                .creationDate("2024-01-01T00:00:00.000Z")
                .ownerId("123456789012")
                .blockDeviceMappings(
                    BlockDeviceMapping
                        .builder()
                        .ebs(
                            EbsBlockDevice
                                .builder()
                                .snapshotId("snap-111")
                                .build(),
                        ).build(),
                ).build()

        val response =
            DescribeImagesResponse
                .builder()
                .images(testImage)
                .build()

        whenever(mockEc2Client.describeImages(any<DescribeImagesRequest>())).thenReturn(response)

        val result = ec2Service.listPrivateAMIs("rustyrazorblade/images/easy-cass-lab-*")

        assertThat(result).hasSize(1)

        val ami = result[0]
        assertThat(ami.id).isEqualTo("ami-111")
        assertThat(ami.name).isEqualTo("rustyrazorblade/images/easy-cass-lab-cassandra-amd64-20240101")
        assertThat(ami.architecture).isEqualTo("amd64")
        assertThat(ami.isPublic).isFalse()
        assertThat(ami.snapshotIds).containsExactly("snap-111")
        assertThat(ami.creationDate).isEqualTo(Instant.parse("2024-01-01T00:00:00.000Z"))

        // Verify correct filters were applied
        verify(mockEc2Client).describeImages(any<DescribeImagesRequest>())
    }

    @Test
    fun `listPrivateAMIs should convert x86_64 architecture to amd64`() {
        val testImage =
            Image
                .builder()
                .imageId("ami-111")
                .name("rustyrazorblade/images/easy-cass-lab-base-amd64-20240101")
                .architecture("x86_64")
                .creationDate("2024-01-01T00:00:00.000Z")
                .ownerId("123456789012")
                .blockDeviceMappings(emptyList())
                .build()

        val response =
            DescribeImagesResponse
                .builder()
                .images(testImage)
                .build()

        whenever(mockEc2Client.describeImages(any<DescribeImagesRequest>())).thenReturn(response)

        val result = ec2Service.listPrivateAMIs("*")

        assertThat(result[0].architecture).isEqualTo("amd64")
    }

    @Test
    fun `listPrivateAMIs should keep arm64 architecture unchanged`() {
        val testImage =
            Image
                .builder()
                .imageId("ami-111")
                .name("rustyrazorblade/images/easy-cass-lab-base-arm64-20240101")
                .architecture("arm64")
                .creationDate("2024-01-01T00:00:00.000Z")
                .ownerId("123456789012")
                .blockDeviceMappings(emptyList())
                .build()

        val response =
            DescribeImagesResponse
                .builder()
                .images(testImage)
                .build()

        whenever(mockEc2Client.describeImages(any<DescribeImagesRequest>())).thenReturn(response)

        val result = ec2Service.listPrivateAMIs("*")

        assertThat(result[0].architecture).isEqualTo("arm64")
    }

    @Test
    fun `listPrivateAMIs should handle multiple block device mappings`() {
        val testImage =
            Image
                .builder()
                .imageId("ami-111")
                .name("rustyrazorblade/images/easy-cass-lab-cassandra-amd64-20240101")
                .architecture("x86_64")
                .creationDate("2024-01-01T00:00:00.000Z")
                .ownerId("123456789012")
                .blockDeviceMappings(
                    BlockDeviceMapping
                        .builder()
                        .ebs(
                            EbsBlockDevice
                                .builder()
                                .snapshotId("snap-111")
                                .build(),
                        ).build(),
                    BlockDeviceMapping
                        .builder()
                        .ebs(
                            EbsBlockDevice
                                .builder()
                                .snapshotId("snap-222")
                                .build(),
                        ).build(),
                ).build()

        val response =
            DescribeImagesResponse
                .builder()
                .images(testImage)
                .build()

        whenever(mockEc2Client.describeImages(any<DescribeImagesRequest>())).thenReturn(response)

        val result = ec2Service.listPrivateAMIs("*")

        assertThat(result[0].snapshotIds).containsExactlyInAnyOrder("snap-111", "snap-222")
    }

    @Test
    fun `getSnapshotsForAMI should return snapshot IDs associated with AMI`() {
        val testSnapshot1 =
            Snapshot
                .builder()
                .snapshotId("snap-111")
                .build()

        val testSnapshot2 =
            Snapshot
                .builder()
                .snapshotId("snap-222")
                .build()

        val response =
            DescribeSnapshotsResponse
                .builder()
                .snapshots(testSnapshot1, testSnapshot2)
                .build()

        whenever(mockEc2Client.describeSnapshots(any<DescribeSnapshotsRequest>())).thenReturn(response)

        val result = ec2Service.getSnapshotsForAMI("ami-111")

        assertThat(result).containsExactlyInAnyOrder("snap-111", "snap-222")

        // Verify correct filter was applied
        verify(mockEc2Client).describeSnapshots(any<DescribeSnapshotsRequest>())
    }

    @Test
    fun `deregisterAMI should call EC2 client deregisterImage`() {
        val response = DeregisterImageResponse.builder().build()
        whenever(mockEc2Client.deregisterImage(any<DeregisterImageRequest>())).thenReturn(response)

        ec2Service.deregisterAMI("ami-111")

        verify(mockEc2Client).deregisterImage(any<DeregisterImageRequest>())
    }

    @Test
    fun `deleteSnapshot should call EC2 client deleteSnapshot`() {
        val response = DeleteSnapshotResponse.builder().build()
        whenever(mockEc2Client.deleteSnapshot(any<DeleteSnapshotRequest>())).thenReturn(response)

        ec2Service.deleteSnapshot("snap-111")

        verify(mockEc2Client).deleteSnapshot(any<DeleteSnapshotRequest>())
    }

    @Test
    fun `should build correct filter for listPrivateAMIs`() {
        val response =
            DescribeImagesResponse
                .builder()
                .images(emptyList())
                .build()

        var capturedRequest: DescribeImagesRequest? = null
        whenever(mockEc2Client.describeImages(any<DescribeImagesRequest>())).thenAnswer { invocation ->
            capturedRequest = invocation.arguments[0] as DescribeImagesRequest
            response
        }

        ec2Service.listPrivateAMIs("rustyrazorblade/images/easy-cass-lab-*")

        assertThat(capturedRequest).isNotNull
        assertThat(capturedRequest!!.owners()).containsExactly("self")
        assertThat(capturedRequest!!.filters()).hasSize(1)

        val filter = capturedRequest!!.filters()[0]
        assertThat(filter.name()).isEqualTo("name")
        assertThat(filter.values()).containsExactly("rustyrazorblade/images/easy-cass-lab-*")
    }
}
