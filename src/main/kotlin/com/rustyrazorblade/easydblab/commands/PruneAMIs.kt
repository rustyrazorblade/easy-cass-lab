package com.rustyrazorblade.easydblab.commands

import com.rustyrazorblade.easydblab.Context
import com.rustyrazorblade.easydblab.annotations.McpCommand
import com.rustyrazorblade.easydblab.annotations.RequireProfileSetup
import com.rustyrazorblade.easydblab.providers.aws.AMIService
import com.rustyrazorblade.easydblab.providers.aws.EC2Service
import org.koin.core.component.inject
import picocli.CommandLine.Command
import picocli.CommandLine.Option

/**
 * Prune older private AMIs while keeping the newest N per architecture and type.
 */
@McpCommand
@RequireProfileSetup
@Command(
    name = "prune-amis",
    description = ["Prune older private AMIs while keeping the newest N per architecture and type"],
)
class PruneAMIs(
    context: Context,
) : PicoBaseCommand(context) {
    private val service: AMIService by inject()
    private val ec2Service: EC2Service by inject()

    @Option(
        names = ["--pattern"],
        description = ["Name pattern for AMIs to prune (supports wildcards)"],
    )
    var pattern: String = "rustyrazorblade/images/easy-db-lab-*"

    @Option(
        names = ["--keep"],
        description = ["Number of newest AMIs to keep per architecture/type combination"],
    )
    var keep: Int = 2

    @Option(
        names = ["--dry-run"],
        description = ["Show what would be deleted without actually deleting"],
    )
    var dryRun: Boolean = false

    @Option(
        names = ["--type"],
        description = ["Filter to only prune AMIs of specific type (e.g., 'cassandra', 'base')"],
    )
    var type: String? = null

    @Suppress("NestedBlockDepth", "TooGenericExceptionCaught")
    override fun execute() {
        outputHandler.publishMessage("Pruning AMIs matching pattern: $pattern")
        if (type != null) {
            outputHandler.publishMessage("Filtering by type: $type")
        }
        outputHandler.publishMessage("Keeping newest $keep AMIs per architecture/type combination")
        outputHandler.publishMessage("")

        // First, identify which AMIs would be deleted (dry-run)
        val preview =
            service.pruneAMIs(
                namePattern = pattern,
                keepCount = keep,
                dryRun = true,
                typeFilter = type,
            )

        // Show what will be kept
        if (preview.kept.isNotEmpty()) {
            outputHandler.publishMessage("Will keep ${preview.kept.size} AMIs:")
            for (ami in preview.kept) {
                outputHandler.publishMessage("  ✓ ${ami.id}: ${ami.name} (${ami.architecture}, ${ami.creationDate})")
            }
            outputHandler.publishMessage("")
        }

        // Check if there's anything to delete
        if (preview.deleted.isEmpty()) {
            outputHandler.publishMessage("No AMIs to delete")
            return
        }

        // If dry-run mode, just show what would be deleted and exit
        if (dryRun) {
            outputHandler.publishMessage("DRY RUN - Would delete ${preview.deleted.size} AMIs:")
            for (ami in preview.deleted) {
                val visibility = if (ami.isPublic) "public" else "private"
                outputHandler.publishMessage("  × ${ami.id}: ${ami.name} (${ami.architecture}, ${ami.creationDate})")
                outputHandler.publishMessage("    Owner: ${ami.ownerId}, Visibility: $visibility")
                if (ami.snapshotIds.isNotEmpty()) {
                    outputHandler.publishMessage("    Snapshots: ${ami.snapshotIds.joinToString(", ")}")
                }
            }
            return
        }

        // Delete AMIs with confirmation (already sorted oldest first by AMIService)
        outputHandler.publishMessage("Found ${preview.deleted.size} AMIs to delete")
        val amisToDelete = preview.deleted

        val actuallyDeleted = mutableListOf<String>()
        val skipped = mutableListOf<String>()

        for (ami in amisToDelete) {
            val visibility = if (ami.isPublic) "public" else "private"
            outputHandler.publishMessage("")
            outputHandler.publishMessage("AMI: ${ami.id}")
            outputHandler.publishMessage("  Name: ${ami.name}")
            outputHandler.publishMessage("  Architecture: ${ami.architecture}")
            outputHandler.publishMessage("  Created: ${ami.creationDate}")
            outputHandler.publishMessage("  Owner: ${ami.ownerId}")
            outputHandler.publishMessage("  Visibility: $visibility")
            if (ami.snapshotIds.isNotEmpty()) {
                outputHandler.publishMessage("  Snapshots: ${ami.snapshotIds.joinToString(", ")}")
            }

            print("Delete this AMI? [y/N]: ")
            val response = readlnOrNull()?.trim()?.lowercase() ?: "n"
            val shouldDelete = response == "y" || response == "yes"

            if (shouldDelete) {
                try {
                    ec2Service.deregisterAMI(ami.id)
                    for (snapshotId in ami.snapshotIds) {
                        ec2Service.deleteSnapshot(snapshotId)
                    }
                    outputHandler.publishMessage("  ✓ Deleted")
                    actuallyDeleted.add(ami.id)
                } catch (e: Exception) {
                    outputHandler.publishMessage("  ✗ Error deleting: ${e.message}")
                }
            } else {
                outputHandler.publishMessage("  - Skipped")
                skipped.add(ami.id)
            }
        }

        // Summary
        outputHandler.publishMessage("")
        outputHandler.publishMessage("Summary:")
        outputHandler.publishMessage("  Deleted: ${actuallyDeleted.size} AMIs")
        outputHandler.publishMessage("  Skipped: ${skipped.size} AMIs")
        outputHandler.publishMessage("  Kept: ${preview.kept.size} AMIs")
    }
}
