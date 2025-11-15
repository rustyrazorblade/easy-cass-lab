package com.rustyrazorblade.easycasslab.providers.aws

import com.rustyrazorblade.easycasslab.providers.aws.model.AMI
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant

internal class AMIServiceTest {
    private val mockEc2Service: EC2Service = mock()
    private val amiService = AMIService(mockEc2Service)

    @Test
    fun `pruneAMIs should keep newest N AMIs per architecture and type`() {
        val amis =
            listOf(
                createAMI("ami-1", "rustyrazorblade/images/easy-cass-lab-cassandra-amd64-20240103", "amd64", "2024-01-03T00:00:00Z"),
                createAMI("ami-2", "rustyrazorblade/images/easy-cass-lab-cassandra-amd64-20240102", "amd64", "2024-01-02T00:00:00Z"),
                createAMI("ami-3", "rustyrazorblade/images/easy-cass-lab-cassandra-amd64-20240101", "amd64", "2024-01-01T00:00:00Z"),
                createAMI("ami-4", "rustyrazorblade/images/easy-cass-lab-cassandra-arm64-20240103", "arm64", "2024-01-03T00:00:00Z"),
                createAMI("ami-5", "rustyrazorblade/images/easy-cass-lab-cassandra-arm64-20240102", "arm64", "2024-01-02T00:00:00Z"),
            )

        whenever(mockEc2Service.listPrivateAMIs(any())).thenReturn(amis)

        val result = amiService.pruneAMIs(namePattern = "rustyrazorblade/images/easy-cass-lab-*", keepCount = 2, dryRun = false)

        // Should delete oldest from amd64 (ami-3), keep all arm64 (only 2 exist)
        assertThat(result.deleted).hasSize(1)
        assertThat(result.deleted.map { it.id }).containsExactly("ami-3")
        assertThat(result.kept).hasSize(4)
        assertThat(result.kept.map { it.id }).containsExactlyInAnyOrder("ami-1", "ami-2", "ami-4", "ami-5")

        verify(mockEc2Service).deregisterAMI("ami-3")
    }

    @Test
    fun `pruneAMIs should handle multiple types independently`() {
        val amis =
            listOf(
                createAMI("ami-1", "rustyrazorblade/images/easy-cass-lab-cassandra-amd64-20240103", "amd64", "2024-01-03T00:00:00Z"),
                createAMI("ami-2", "rustyrazorblade/images/easy-cass-lab-cassandra-amd64-20240102", "amd64", "2024-01-02T00:00:00Z"),
                createAMI("ami-3", "rustyrazorblade/images/easy-cass-lab-base-amd64-20240103", "amd64", "2024-01-03T00:00:00Z"),
                createAMI("ami-4", "rustyrazorblade/images/easy-cass-lab-base-amd64-20240102", "amd64", "2024-01-02T00:00:00Z"),
            )

        whenever(mockEc2Service.listPrivateAMIs(any())).thenReturn(amis)

        val result = amiService.pruneAMIs(namePattern = "rustyrazorblade/images/easy-cass-lab-*", keepCount = 1, dryRun = false)

        // Should delete oldest from each type-arch group (ami-2 from cassandra-amd64, ami-4 from base-amd64)
        assertThat(result.deleted).hasSize(2)
        assertThat(result.deleted.map { it.id }).containsExactlyInAnyOrder("ami-2", "ami-4")
        assertThat(result.kept).hasSize(2)
        assertThat(result.kept.map { it.id }).containsExactlyInAnyOrder("ami-1", "ami-3")

        verify(mockEc2Service).deregisterAMI("ami-2")
        verify(mockEc2Service).deregisterAMI("ami-4")
    }

    @Test
    fun `pruneAMIs in dry-run mode should not delete AMIs`() {
        val amis =
            listOf(
                createAMI("ami-1", "rustyrazorblade/images/easy-cass-lab-cassandra-amd64-20240103", "amd64", "2024-01-03T00:00:00Z"),
                createAMI("ami-2", "rustyrazorblade/images/easy-cass-lab-cassandra-amd64-20240102", "amd64", "2024-01-02T00:00:00Z"),
                createAMI("ami-3", "rustyrazorblade/images/easy-cass-lab-cassandra-amd64-20240101", "amd64", "2024-01-01T00:00:00Z"),
            )

        whenever(mockEc2Service.listPrivateAMIs(any())).thenReturn(amis)

        val result = amiService.pruneAMIs(namePattern = "rustyrazorblade/images/easy-cass-lab-*", keepCount = 2, dryRun = true)

        // Should identify AMIs to delete but not actually delete them
        assertThat(result.deleted).hasSize(1)
        assertThat(result.deleted.map { it.id }).containsExactly("ami-3")
        assertThat(result.kept).hasSize(2)

        verify(mockEc2Service, never()).deregisterAMI(any())
        verify(mockEc2Service, never()).deleteSnapshot(any())
    }

    @Test
    fun `pruneAMIs should delete associated snapshots`() {
        val amis =
            listOf(
                createAMI(
                    "ami-1",
                    "rustyrazorblade/images/easy-cass-lab-cassandra-amd64-20240103",
                    "amd64",
                    "2024-01-03T00:00:00Z",
                    listOf("snap-1"),
                ),
                createAMI(
                    "ami-2",
                    "rustyrazorblade/images/easy-cass-lab-cassandra-amd64-20240102",
                    "amd64",
                    "2024-01-02T00:00:00Z",
                    listOf("snap-2", "snap-3"),
                ),
            )

        whenever(mockEc2Service.listPrivateAMIs(any())).thenReturn(amis)

        val result = amiService.pruneAMIs(namePattern = "rustyrazorblade/images/easy-cass-lab-*", keepCount = 1, dryRun = false)

        // Should delete ami-2 and both its snapshots
        assertThat(result.deleted).hasSize(1)
        assertThat(result.deleted[0].id).isEqualTo("ami-2")

        verify(mockEc2Service).deregisterAMI("ami-2")
        verify(mockEc2Service).deleteSnapshot("snap-2")
        verify(mockEc2Service).deleteSnapshot("snap-3")
        verify(mockEc2Service, never()).deleteSnapshot("snap-1")
    }

    @Test
    fun `pruneAMIs should filter by AMI type when specified`() {
        val amis =
            listOf(
                createAMI("ami-1", "rustyrazorblade/images/easy-cass-lab-cassandra-amd64-20240103", "amd64", "2024-01-03T00:00:00Z"),
                createAMI("ami-2", "rustyrazorblade/images/easy-cass-lab-cassandra-amd64-20240102", "amd64", "2024-01-02T00:00:00Z"),
                createAMI("ami-3", "rustyrazorblade/images/easy-cass-lab-base-amd64-20240103", "amd64", "2024-01-03T00:00:00Z"),
                createAMI("ami-4", "rustyrazorblade/images/easy-cass-lab-base-amd64-20240102", "amd64", "2024-01-02T00:00:00Z"),
            )

        whenever(mockEc2Service.listPrivateAMIs(any())).thenReturn(amis)

        val result =
            amiService.pruneAMIs(
                namePattern = "rustyrazorblade/images/easy-cass-lab-*",
                keepCount = 1,
                dryRun = false,
                typeFilter = "cassandra",
            )

        // Should only prune cassandra AMIs, not base AMIs
        assertThat(result.deleted).hasSize(1)
        assertThat(result.deleted.map { it.id }).containsExactly("ami-2")
        assertThat(result.kept).hasSize(1)
        assertThat(result.kept.map { it.id }).containsExactly("ami-1")

        verify(mockEc2Service).deregisterAMI("ami-2")
        verify(mockEc2Service, never()).deregisterAMI("ami-3")
        verify(mockEc2Service, never()).deregisterAMI("ami-4")
    }

    @Test
    fun `pruneAMIs should keep all AMIs when keepCount is greater than or equal to available`() {
        val amis =
            listOf(
                createAMI("ami-1", "rustyrazorblade/images/easy-cass-lab-cassandra-amd64-20240103", "amd64", "2024-01-03T00:00:00Z"),
                createAMI("ami-2", "rustyrazorblade/images/easy-cass-lab-cassandra-amd64-20240102", "amd64", "2024-01-02T00:00:00Z"),
            )

        whenever(mockEc2Service.listPrivateAMIs(any())).thenReturn(amis)

        val result = amiService.pruneAMIs(namePattern = "rustyrazorblade/images/easy-cass-lab-*", keepCount = 5, dryRun = false)

        // Should keep all AMIs since keepCount >= available
        assertThat(result.deleted).isEmpty()
        assertThat(result.kept).hasSize(2)
        assertThat(result.kept.map { it.id }).containsExactlyInAnyOrder("ami-1", "ami-2")

        verify(mockEc2Service, never()).deregisterAMI(any())
    }

    @Test
    fun `pruneAMIs should handle empty AMI list`() {
        whenever(mockEc2Service.listPrivateAMIs(any())).thenReturn(emptyList())

        val result = amiService.pruneAMIs(namePattern = "rustyrazorblade/images/easy-cass-lab-*", keepCount = 2, dryRun = false)

        assertThat(result.deleted).isEmpty()
        assertThat(result.kept).isEmpty()

        verify(mockEc2Service, never()).deregisterAMI(any())
    }

    @Test
    fun `pruneAMIs should handle AMIs without snapshots`() {
        val amis =
            listOf(
                createAMI(
                    "ami-1",
                    "rustyrazorblade/images/easy-cass-lab-cassandra-amd64-20240103",
                    "amd64",
                    "2024-01-03T00:00:00Z",
                    emptyList(),
                ),
                createAMI(
                    "ami-2",
                    "rustyrazorblade/images/easy-cass-lab-cassandra-amd64-20240102",
                    "amd64",
                    "2024-01-02T00:00:00Z",
                    emptyList(),
                ),
            )

        whenever(mockEc2Service.listPrivateAMIs(any())).thenReturn(amis)

        val result = amiService.pruneAMIs(namePattern = "rustyrazorblade/images/easy-cass-lab-*", keepCount = 1, dryRun = false)

        // Should delete AMI but not try to delete any snapshots
        assertThat(result.deleted).hasSize(1)
        assertThat(result.deleted[0].id).isEqualTo("ami-2")

        verify(mockEc2Service).deregisterAMI("ami-2")
        verify(mockEc2Service, never()).deleteSnapshot(any())
    }

    @Test
    fun `pruneAMIs should respect type filter case-insensitively`() {
        val amis =
            listOf(
                createAMI("ami-1", "rustyrazorblade/images/easy-cass-lab-cassandra-amd64-20240103", "amd64", "2024-01-03T00:00:00Z"),
                createAMI("ami-2", "rustyrazorblade/images/easy-cass-lab-cassandra-amd64-20240102", "amd64", "2024-01-02T00:00:00Z"),
                createAMI("ami-3", "rustyrazorblade/images/easy-cass-lab-base-amd64-20240103", "amd64", "2024-01-03T00:00:00Z"),
            )

        whenever(mockEc2Service.listPrivateAMIs(any())).thenReturn(amis)

        val result =
            amiService.pruneAMIs(
                namePattern = "rustyrazorblade/images/easy-cass-lab-*",
                keepCount = 1,
                dryRun = false,
                typeFilter = "CASSANDRA",
            )

        // Should match "cassandra" case-insensitively
        assertThat(result.deleted).hasSize(1)
        assertThat(result.deleted.map { it.id }).containsExactly("ami-2")

        verify(mockEc2Service).deregisterAMI("ami-2")
        verify(mockEc2Service, never()).deregisterAMI("ami-3")
    }

    @Test
    fun `pruneAMIs should return kept AMIs sorted by groupKey`() {
        val amis =
            listOf(
                // Create AMIs in random order to verify sorting
                createAMI("ami-4", "rustyrazorblade/images/easy-cass-lab-cassandra-arm64-20240103", "arm64", "2024-01-03T00:00:00Z"),
                createAMI("ami-1", "rustyrazorblade/images/easy-cass-lab-cassandra-amd64-20240103", "amd64", "2024-01-03T00:00:00Z"),
                createAMI("ami-3", "rustyrazorblade/images/easy-cass-lab-base-arm64-20240103", "arm64", "2024-01-03T00:00:00Z"),
                createAMI("ami-2", "rustyrazorblade/images/easy-cass-lab-base-amd64-20240103", "amd64", "2024-01-03T00:00:00Z"),
            )

        whenever(mockEc2Service.listPrivateAMIs(any())).thenReturn(amis)

        val result = amiService.pruneAMIs(namePattern = "rustyrazorblade/images/easy-cass-lab-*", keepCount = 1, dryRun = true)

        // Verify kept AMIs are sorted by groupKey (base-amd64, base-arm64, cassandra-amd64, cassandra-arm64)
        assertThat(result.kept).hasSize(4)
        assertThat(result.kept.map { it.id }).containsExactly("ami-2", "ami-3", "ami-1", "ami-4")
    }

    @Test
    fun `pruneAMIs should return deleted AMIs sorted by creation date descending`() {
        val amis =
            listOf(
                // Create AMIs in random order to verify sorting
                createAMI("ami-2", "rustyrazorblade/images/easy-cass-lab-cassandra-amd64-20240102", "amd64", "2024-01-02T00:00:00Z"),
                createAMI("ami-4", "rustyrazorblade/images/easy-cass-lab-cassandra-amd64-20240104", "amd64", "2024-01-04T00:00:00Z"),
                createAMI("ami-1", "rustyrazorblade/images/easy-cass-lab-cassandra-amd64-20240101", "amd64", "2024-01-01T00:00:00Z"),
                createAMI("ami-3", "rustyrazorblade/images/easy-cass-lab-cassandra-amd64-20240103", "amd64", "2024-01-03T00:00:00Z"),
            )

        whenever(mockEc2Service.listPrivateAMIs(any())).thenReturn(amis)

        val result = amiService.pruneAMIs(namePattern = "rustyrazorblade/images/easy-cass-lab-*", keepCount = 1, dryRun = true)

        // Verify deleted AMIs are sorted by creation date descending (oldest first for deletion)
        // Should keep ami-4 (newest), delete ami-3, ami-2, ami-1 in that order (descending)
        assertThat(result.deleted).hasSize(3)
        assertThat(result.deleted.map { it.id }).containsExactly("ami-3", "ami-2", "ami-1")

        // Verify the dates are in descending order
        assertThat(result.deleted[0].creationDate).isAfter(result.deleted[1].creationDate)
        assertThat(result.deleted[1].creationDate).isAfter(result.deleted[2].creationDate)
    }

    private fun createAMI(
        id: String,
        name: String,
        architecture: String,
        creationDate: String,
        snapshotIds: List<String> = emptyList(),
        ownerId: String = "123456789012",
        isPublic: Boolean = false,
    ): AMI =
        AMI(
            id = id,
            name = name,
            architecture = architecture,
            creationDate = Instant.parse(creationDate),
            ownerId = ownerId,
            isPublic = isPublic,
            snapshotIds = snapshotIds,
        )
}
