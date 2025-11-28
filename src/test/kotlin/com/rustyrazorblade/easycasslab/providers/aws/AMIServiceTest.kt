package com.rustyrazorblade.easycasslab.providers.aws

import com.rustyrazorblade.easycasslab.BaseKoinTest
import com.rustyrazorblade.easycasslab.providers.aws.model.AMI
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.test.inject
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import com.rustyrazorblade.easycasslab.assertions.assertThat as assertThatPruneResult

/**
 * Unit tests for AMIService pruning operations.
 *
 * Tests cover:
 * - Basic pruning behavior by architecture and type
 * - Dry-run mode
 * - Snapshot deletion
 * - Type filtering
 * - Edge cases (empty lists, malformed data)
 * - Sorting behavior of results
 */
internal class AMIServiceTest : BaseKoinTest() {
    private val mockEc2Service: EC2Service by inject()
    private val amiService: AMIService by inject()

    override fun additionalTestModules(): List<Module> =
        listOf(
            module {
                single<EC2Service> { mock() }
                single { AMIService(get()) }
            },
        )

    @Test
    fun `pruneAMIs should keep newest N AMIs per architecture and type`() {
        val amis =
            listOf(
                cassandraAMI("ami-1", "amd64", "20240103"),
                cassandraAMI("ami-2", "amd64", "20240102"),
                cassandraAMI("ami-3", "amd64", "20240101"),
                cassandraAMI("ami-4", "arm64", "20240103"),
                cassandraAMI("ami-5", "arm64", "20240102"),
            )

        whenever(mockEc2Service.listPrivateAMIs(any(), any())).thenReturn(amis)

        val result = amiService.pruneAMIs(namePattern = NAME_PATTERN, keepCount = 2, dryRun = false)

        // Should delete oldest from amd64 (ami-3), keep all arm64 (only 2 exist)
        assertThatPruneResult(result)
            .hasDeletedCount(1)
            .hasKeptCount(4)
            .deletedAMIsWithIds("ami-3")
            .keptAMIsWithIds("ami-1", "ami-2", "ami-4", "ami-5")

        verify(mockEc2Service).deregisterAMI("ami-3")
    }

    @Test
    fun `pruneAMIs should handle multiple types independently`() {
        val amis =
            listOf(
                cassandraAMI("ami-1", "amd64", "20240103"),
                cassandraAMI("ami-2", "amd64", "20240102"),
                baseAMI("ami-3", "amd64", "20240103"),
                baseAMI("ami-4", "amd64", "20240102"),
            )

        whenever(mockEc2Service.listPrivateAMIs(any(), any())).thenReturn(amis)

        val result = amiService.pruneAMIs(namePattern = NAME_PATTERN, keepCount = 1, dryRun = false)

        // Should delete oldest from each type-arch group (ami-2 from cassandra-amd64, ami-4 from base-amd64)
        assertThatPruneResult(result)
            .hasDeletedCount(2)
            .hasKeptCount(2)
            .deletedAMIsWithIds("ami-2", "ami-4")
            .keptAMIsWithIds("ami-1", "ami-3")

        verify(mockEc2Service).deregisterAMI("ami-2")
        verify(mockEc2Service).deregisterAMI("ami-4")
    }

    @Test
    fun `pruneAMIs in dry-run mode should not delete AMIs`() {
        val amis =
            listOf(
                cassandraAMI("ami-1", "amd64", "20240103"),
                cassandraAMI("ami-2", "amd64", "20240102"),
                cassandraAMI("ami-3", "amd64", "20240101"),
            )

        whenever(mockEc2Service.listPrivateAMIs(any(), any())).thenReturn(amis)

        val result = amiService.pruneAMIs(namePattern = NAME_PATTERN, keepCount = 2, dryRun = true)

        // Should identify AMIs to delete but not actually delete them
        assertThatPruneResult(result)
            .hasDeletedCount(1)
            .hasKeptCount(2)
            .deletedAMIsWithIds("ami-3")

        verify(mockEc2Service, never()).deregisterAMI(any())
        verify(mockEc2Service, never()).deleteSnapshot(any())
    }

    @Test
    fun `pruneAMIs should delete associated snapshots`() {
        val amis =
            listOf(
                cassandraAMI("ami-1", "amd64", "20240103", listOf("snap-1")),
                cassandraAMI("ami-2", "amd64", "20240102", listOf("snap-2", "snap-3")),
            )

        whenever(mockEc2Service.listPrivateAMIs(any(), any())).thenReturn(amis)

        val result = amiService.pruneAMIs(namePattern = NAME_PATTERN, keepCount = 1, dryRun = false)

        // Should delete ami-2 and both its snapshots
        assertThatPruneResult(result)
            .hasDeletedCount(1)
            .deletedAMIsWithIds("ami-2")

        verify(mockEc2Service).deregisterAMI("ami-2")
        verify(mockEc2Service).deleteSnapshot("snap-2")
        verify(mockEc2Service).deleteSnapshot("snap-3")
        verify(mockEc2Service, never()).deleteSnapshot("snap-1")
    }

    @Test
    fun `pruneAMIs should filter by AMI type when specified`() {
        val amis =
            listOf(
                cassandraAMI("ami-1", "amd64", "20240103"),
                cassandraAMI("ami-2", "amd64", "20240102"),
                baseAMI("ami-3", "amd64", "20240103"),
                baseAMI("ami-4", "amd64", "20240102"),
            )

        whenever(mockEc2Service.listPrivateAMIs(any(), any())).thenReturn(amis)

        val result =
            amiService.pruneAMIs(
                namePattern = NAME_PATTERN,
                keepCount = 1,
                dryRun = false,
                typeFilter = "cassandra",
            )

        // Should only prune cassandra AMIs, not base AMIs
        assertThatPruneResult(result)
            .hasDeletedCount(1)
            .hasKeptCount(1)
            .deletedAMIsWithIds("ami-2")
            .keptAMIsWithIds("ami-1")

        verify(mockEc2Service).deregisterAMI("ami-2")
        verify(mockEc2Service, never()).deregisterAMI("ami-3")
        verify(mockEc2Service, never()).deregisterAMI("ami-4")
    }

    @Test
    fun `pruneAMIs should keep all AMIs when keepCount is greater than or equal to available`() {
        val amis =
            listOf(
                cassandraAMI("ami-1", "amd64", "20240103"),
                cassandraAMI("ami-2", "amd64", "20240102"),
            )

        whenever(mockEc2Service.listPrivateAMIs(any(), any())).thenReturn(amis)

        val result = amiService.pruneAMIs(namePattern = NAME_PATTERN, keepCount = 5, dryRun = false)

        // Should keep all AMIs since keepCount >= available
        assertThatPruneResult(result)
            .hasNoDeleted()
            .hasKeptCount(2)
            .keptAMIsWithIds("ami-1", "ami-2")

        verify(mockEc2Service, never()).deregisterAMI(any())
    }

    @Test
    fun `pruneAMIs should handle empty AMI list`() {
        whenever(mockEc2Service.listPrivateAMIs(any(), any())).thenReturn(emptyList())

        val result = amiService.pruneAMIs(namePattern = NAME_PATTERN, keepCount = 2, dryRun = false)

        assertThatPruneResult(result)
            .hasNoDeleted()
            .hasNoKept()

        verify(mockEc2Service, never()).deregisterAMI(any())
    }

    @Test
    fun `pruneAMIs should handle AMIs without snapshots`() {
        val amis =
            listOf(
                cassandraAMI("ami-1", "amd64", "20240103"),
                cassandraAMI("ami-2", "amd64", "20240102"),
            )

        whenever(mockEc2Service.listPrivateAMIs(any(), any())).thenReturn(amis)

        val result = amiService.pruneAMIs(namePattern = NAME_PATTERN, keepCount = 1, dryRun = false)

        // Should delete AMI but not try to delete any snapshots
        assertThatPruneResult(result)
            .hasDeletedCount(1)
            .deletedAMIsWithIds("ami-2")

        verify(mockEc2Service).deregisterAMI("ami-2")
        verify(mockEc2Service, never()).deleteSnapshot(any())
    }

    @Test
    fun `pruneAMIs should respect type filter case-insensitively`() {
        val amis =
            listOf(
                cassandraAMI("ami-1", "amd64", "20240103"),
                cassandraAMI("ami-2", "amd64", "20240102"),
                baseAMI("ami-3", "amd64", "20240103"),
            )

        whenever(mockEc2Service.listPrivateAMIs(any(), any())).thenReturn(amis)

        val result =
            amiService.pruneAMIs(
                namePattern = NAME_PATTERN,
                keepCount = 1,
                dryRun = false,
                typeFilter = "CASSANDRA",
            )

        // Should match "cassandra" case-insensitively
        assertThatPruneResult(result)
            .hasDeletedCount(1)
            .deletedAMIsWithIds("ami-2")

        verify(mockEc2Service).deregisterAMI("ami-2")
        verify(mockEc2Service, never()).deregisterAMI("ami-3")
    }

    @Test
    fun `pruneAMIs should return kept AMIs sorted by groupKey`() {
        val amis =
            listOf(
                // Create AMIs in random order to verify sorting
                cassandraAMI("ami-4", "arm64", "20240103"),
                cassandraAMI("ami-1", "amd64", "20240103"),
                baseAMI("ami-3", "arm64", "20240103"),
                baseAMI("ami-2", "amd64", "20240103"),
            )

        whenever(mockEc2Service.listPrivateAMIs(any(), any())).thenReturn(amis)

        val result = amiService.pruneAMIs(namePattern = NAME_PATTERN, keepCount = 1, dryRun = true)

        // Verify kept AMIs are sorted by groupKey (base-amd64, base-arm64, cassandra-amd64, cassandra-arm64)
        assertThatPruneResult(result)
            .hasKeptCount(4)
            .keptAMIsInOrder("ami-2", "ami-3", "ami-1", "ami-4")
    }

    @Test
    fun `pruneAMIs should return deleted AMIs sorted by creation date ascending`() {
        val amis =
            listOf(
                // Create AMIs in random order to verify sorting
                cassandraAMI("ami-2", "amd64", "20240102"),
                cassandraAMI("ami-4", "amd64", "20240104"),
                cassandraAMI("ami-1", "amd64", "20240101"),
                cassandraAMI("ami-3", "amd64", "20240103"),
            )

        whenever(mockEc2Service.listPrivateAMIs(any(), any())).thenReturn(amis)

        val result = amiService.pruneAMIs(namePattern = NAME_PATTERN, keepCount = 1, dryRun = true)

        // Verify deleted AMIs are sorted by creation date ascending (oldest first for deletion)
        // Should keep ami-4 (newest), delete ami-1, ami-2, ami-3 in that order (ascending/oldest first)
        assertThatPruneResult(result)
            .hasDeletedCount(3)
            .deletedAMIsInOrder("ami-1", "ami-2", "ami-3")
            .deletedAMIsAreSortedByCreationDateAscending()

        // Verify the dates are in ascending order (oldest first)
        assertThat(result.deleted[0].creationDate).isBefore(result.deleted[1].creationDate)
        assertThat(result.deleted[1].creationDate).isBefore(result.deleted[2].creationDate)
    }

    @Test
    fun `pruneAMIs should handle AMIs with malformed names gracefully`() {
        val amis =
            listOf(
                // Valid AMI
                cassandraAMI("ami-1", "amd64", "20240103"),
                // AMI with malformed name (missing type/arch parts)
                createAMI("ami-2", "malformed-name", "amd64", "2024-01-02T00:00:00Z"),
                // Another valid AMI
                cassandraAMI("ami-3", "amd64", "20240101"),
            )

        whenever(mockEc2Service.listPrivateAMIs(any(), any())).thenReturn(amis)

        // Should handle malformed names gracefully (they get type "unknown")
        val result = amiService.pruneAMIs(namePattern = NAME_PATTERN, keepCount = 1, dryRun = true)

        // ami-1 kept (cassandra-amd64 newest), ami-2 kept (unknown-amd64 only one), ami-3 deleted
        assertThatPruneResult(result)
            .hasDeletedCount(1)
            .hasKeptCount(2)
    }

    @Test
    fun `pruneAMIs should handle single AMI in each group`() {
        val amis =
            listOf(
                cassandraAMI("ami-1", "amd64", "20240103"),
                cassandraAMI("ami-2", "arm64", "20240103"),
                baseAMI("ami-3", "amd64", "20240103"),
            )

        whenever(mockEc2Service.listPrivateAMIs(any(), any())).thenReturn(amis)

        val result = amiService.pruneAMIs(namePattern = NAME_PATTERN, keepCount = 1, dryRun = false)

        // Each group has exactly one AMI, so nothing should be deleted
        assertThatPruneResult(result)
            .hasNoDeleted()
            .hasKeptCount(3)
            .keptAMIsWithIds("ami-1", "ami-2", "ami-3")

        verify(mockEc2Service, never()).deregisterAMI(any())
    }

    // ===== Test Data Builders =====

    companion object {
        private const val DEFAULT_OWNER = "123456789012"
        private const val NAME_PREFIX = "rustyrazorblade/images/easy-cass-lab"
        private const val NAME_PATTERN = "$NAME_PREFIX-*"

        /**
         * Creates a Cassandra AMI with standard naming convention.
         */
        fun cassandraAMI(
            id: String,
            arch: String,
            date: String,
            snapshots: List<String> = emptyList(),
        ): AMI =
            createAMI(
                id = id,
                name = "$NAME_PREFIX-cassandra-$arch-$date",
                architecture = arch,
                creationDate = "${date.substring(0, 4)}-${date.substring(4, 6)}-${date.substring(6, 8)}T00:00:00Z",
                snapshotIds = snapshots,
            )

        /**
         * Creates a base AMI with standard naming convention.
         */
        fun baseAMI(
            id: String,
            arch: String,
            date: String,
            snapshots: List<String> = emptyList(),
        ): AMI =
            createAMI(
                id = id,
                name = "$NAME_PREFIX-base-$arch-$date",
                architecture = arch,
                creationDate = "${date.substring(0, 4)}-${date.substring(4, 6)}-${date.substring(6, 8)}T00:00:00Z",
                snapshotIds = snapshots,
            )

        /**
         * Creates an AMI with full customization.
         */
        fun createAMI(
            id: String,
            name: String,
            architecture: String,
            creationDate: String,
            snapshotIds: List<String> = emptyList(),
            ownerId: String = DEFAULT_OWNER,
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
}
