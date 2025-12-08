package com.rustyrazorblade.easydblab.providers.aws

import com.rustyrazorblade.easydblab.providers.aws.model.AMI

/**
 * Service for managing AMI lifecycle and pruning operations.
 *
 * This service provides high-level operations for managing private AMIs,
 * including identifying older AMIs to prune while keeping the newest N AMIs
 * for each combination of architecture and type.
 *
 * @property ec2Service Low-level EC2 operations service
 */
class AMIService(
    private val ec2Service: EC2Service,
) {
    /**
     * Result of a pruning operation.
     *
     * @property kept List of AMIs that were kept (not deleted), sorted by groupKey for display
     * @property deleted List of AMIs that were deleted (or would be deleted in dry-run mode),
     *                   sorted by creation date ascending (oldest first)
     */
    data class PruneResult(
        val kept: List<AMI>,
        val deleted: List<AMI>,
    )

    /**
     * Prunes older AMIs while keeping the newest N AMIs per architecture and type.
     *
     * This method groups AMIs by their type and architecture, sorts them by creation date
     * (newest first), and identifies older AMIs to delete while keeping the specified count.
     * When dry-run is enabled, it identifies AMIs to delete without actually deleting them.
     *
     * @param namePattern Wildcard pattern for filtering AMI names
     * @param keepCount Number of newest AMIs to keep per architecture/type combination
     * @param dryRun If true, identify AMIs to delete but don't actually delete them
     * @param typeFilter Optional filter to only prune AMIs of specific type (e.g., "cassandra", "base")
     * @return PruneResult containing lists of kept and deleted AMIs
     */
    fun pruneAMIs(
        namePattern: String,
        keepCount: Int,
        dryRun: Boolean,
        typeFilter: String? = null,
    ): PruneResult {
        // Fetch all private AMIs matching the pattern
        val allAMIs = ec2Service.listPrivateAMIs(namePattern)

        // Apply type filter if specified (case-insensitive)
        val filteredAMIs =
            if (typeFilter != null) {
                allAMIs.filter { it.type.equals(typeFilter, ignoreCase = true) }
            } else {
                allAMIs
            }

        // Group AMIs by type and architecture (e.g., "cassandra-amd64", "base-arm64")
        val groupedAMIs = filteredAMIs.groupBy { it.groupKey }

        val keptAMIs = mutableListOf<AMI>()
        val deletedAMIs = mutableListOf<AMI>()

        // Process each group independently
        for ((_, amis) in groupedAMIs) {
            // Sort by creation date (newest first) using AMI's compareTo implementation
            val sortedAMIs = amis.sorted()

            // Keep the newest N AMIs
            val toKeep = sortedAMIs.take(keepCount)
            val toDelete = sortedAMIs.drop(keepCount)

            keptAMIs.addAll(toKeep)
            deletedAMIs.addAll(toDelete)

            // Delete AMIs and their snapshots if not in dry-run mode
            if (!dryRun) {
                deleteAMIsWithSnapshots(toDelete)
            }
        }

        // Sort kept AMIs by groupKey for display purposes
        val sortedKept = keptAMIs.sortedBy { it.groupKey }

        // Sort deleted AMIs by creation date ascending (oldest first) for deletion order
        val sortedDeleted = deletedAMIs.sortedBy { it.creationDate }

        return PruneResult(kept = sortedKept, deleted = sortedDeleted)
    }

    /**
     * Deletes AMIs and their associated snapshots.
     *
     * @param amis List of AMIs to delete
     */
    private fun deleteAMIsWithSnapshots(amis: List<AMI>) {
        for (ami in amis) {
            ec2Service.deregisterAMI(ami.id)
            ami.snapshotIds.forEach { snapshotId ->
                ec2Service.deleteSnapshot(snapshotId)
            }
        }
    }
}
