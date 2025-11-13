package com.rustyrazorblade.easycasslab.commands

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.annotations.McpCommand
import com.rustyrazorblade.easycasslab.providers.aws.AMIService
import com.rustyrazorblade.easycasslab.providers.aws.EC2Service
import org.koin.core.component.inject

@McpCommand
@Parameters(
    commandDescription = "Prune older private AMIs while keeping the newest N per architecture and type",
    commandNames = ["prune-amis"],
)
class PruneAMIs(
    context: Context,
    amiService: AMIService? = null,
) : BaseCommand(context) {
    private val service: AMIService = amiService ?: inject<AMIService>().value
    private val ec2Service: EC2Service by inject()

    @Parameter(
        names = ["--pattern"],
        description = "Name pattern for AMIs to prune (supports wildcards)",
    )
    var pattern: String = "rustyrazorblade/images/easy-cass-lab-*"

    @Parameter(
        names = ["--keep"],
        description = "Number of newest AMIs to keep per architecture/type combination",
    )
    var keep: Int = 2

    @Parameter(
        names = ["--dry-run"],
        description = "Show what would be deleted without actually deleting",
    )
    var dryRun: Boolean = false

    @Parameter(
        names = ["--type"],
        description = "Filter to only prune AMIs of specific type (e.g., 'cassandra', 'base')",
    )
    var type: String? = null

    @Parameter(
        names = ["--yes", "-y"],
        description = "Skip confirmation prompts and delete all identified AMIs",
    )
    var skipConfirmation: Boolean = false

    override fun execute() {
        outputHandler.handleMessage("Pruning AMIs matching pattern: $pattern")
        if (type != null) {
            outputHandler.handleMessage("Filtering by type: $type")
        }
        outputHandler.handleMessage("Keeping newest $keep AMIs per architecture/type combination")
        outputHandler.handleMessage("")

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
            outputHandler.handleMessage("Will keep ${preview.kept.size} AMIs:")
            for (ami in preview.kept.sortedBy { it.groupKey }) {
                outputHandler.handleMessage("  ✓ ${ami.id}: ${ami.name} (${ami.architecture}, ${ami.creationDate})")
            }
            outputHandler.handleMessage("")
        }

        // Check if there's anything to delete
        if (preview.deleted.isEmpty()) {
            outputHandler.handleMessage("No AMIs to delete")
            return
        }

        // If dry-run mode, just show what would be deleted and exit
        if (dryRun) {
            outputHandler.handleMessage("DRY RUN - Would delete ${preview.deleted.size} AMIs:")
            for (ami in preview.deleted.sortedByDescending { it.creationDate }) {
                outputHandler.handleMessage("  × ${ami.id}: ${ami.name} (${ami.architecture}, ${ami.creationDate})")
                if (ami.snapshotIds.isNotEmpty()) {
                    outputHandler.handleMessage("    Snapshots: ${ami.snapshotIds.joinToString(", ")}")
                }
            }
            return
        }

        // Delete AMIs with confirmation (oldest first)
        outputHandler.handleMessage("Found ${preview.deleted.size} AMIs to delete")
        val amisToDelete = preview.deleted.sortedByDescending { it.creationDate } // Oldest first

        val actuallyDeleted = mutableListOf<String>()
        val skipped = mutableListOf<String>()

        for (ami in amisToDelete) {
            outputHandler.handleMessage("")
            outputHandler.handleMessage("AMI: ${ami.id}")
            outputHandler.handleMessage("  Name: ${ami.name}")
            outputHandler.handleMessage("  Architecture: ${ami.architecture}")
            outputHandler.handleMessage("  Created: ${ami.creationDate}")
            if (ami.snapshotIds.isNotEmpty()) {
                outputHandler.handleMessage("  Snapshots: ${ami.snapshotIds.joinToString(", ")}")
            }

            val shouldDelete =
                if (skipConfirmation) {
                    true
                } else {
                    print("Delete this AMI? [y/N]: ")
                    val response = readLine()?.trim()?.lowercase() ?: "n"
                    response == "y" || response == "yes"
                }

            if (shouldDelete) {
                try {
                    ec2Service.deregisterAMI(ami.id)
                    for (snapshotId in ami.snapshotIds) {
                        ec2Service.deleteSnapshot(snapshotId)
                    }
                    outputHandler.handleMessage("  ✓ Deleted")
                    actuallyDeleted.add(ami.id)
                } catch (e: Exception) {
                    outputHandler.handleMessage("  ✗ Error deleting: ${e.message}")
                }
            } else {
                outputHandler.handleMessage("  - Skipped")
                skipped.add(ami.id)
            }
        }

        // Summary
        outputHandler.handleMessage("")
        outputHandler.handleMessage("Summary:")
        outputHandler.handleMessage("  Deleted: ${actuallyDeleted.size} AMIs")
        outputHandler.handleMessage("  Skipped: ${skipped.size} AMIs")
        outputHandler.handleMessage("  Kept: ${preview.kept.size} AMIs")
    }
}
