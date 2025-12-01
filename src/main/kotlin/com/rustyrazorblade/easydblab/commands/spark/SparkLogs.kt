package com.rustyrazorblade.easydblab.commands.spark

import com.rustyrazorblade.easydblab.Context
import com.rustyrazorblade.easydblab.annotations.McpCommand
import com.rustyrazorblade.easydblab.annotations.RequireProfileSetup
import com.rustyrazorblade.easydblab.commands.PicoBaseCommand
import com.rustyrazorblade.easydblab.services.SparkService
import org.koin.core.component.inject
import picocli.CommandLine.Command
import picocli.CommandLine.Option

/**
 * Download EMR logs from S3 to local directory.
 *
 * By default, downloads only the step-specific logs (stdout and stderr) which are
 * most useful for debugging Spark job failures. Use --all to download the complete
 * EMR logs directory including node logs, container logs, and other infrastructure logs.
 *
 * Usage:
 * - `spark logs` - Downloads step logs (stdout, stderr) for most recent job
 * - `spark logs --step-id s-XXXXX` - Downloads step logs for specific job
 * - `spark logs --all` - Downloads all EMR logs (node logs, containers, etc.)
 */
@McpCommand
@RequireProfileSetup
@Command(
    name = "logs",
    description = ["Download EMR logs from S3"],
)
class SparkLogs(
    context: Context,
) : PicoBaseCommand(context) {
    private val sparkService: SparkService by inject()

    @Option(
        names = ["--step-id"],
        description = ["EMR step ID (defaults to most recent job)"],
    )
    var stepId: String? = null

    @Option(
        names = ["--all"],
        description = ["Download all EMR logs (node logs, containers, etc.) instead of just step logs"],
    )
    var downloadAll: Boolean = false

    override fun execute() {
        // Validate cluster exists and is accessible
        val clusterInfo =
            sparkService
                .validateCluster()
                .getOrElse { error ->
                    error(error.message ?: "Failed to validate EMR cluster")
                }

        // Determine step ID - use provided or get most recent
        val targetStepId =
            stepId ?: getMostRecentStepId(clusterInfo.clusterId)

        val logsDir =
            if (downloadAll) {
                outputHandler.handleMessage("Downloading all EMR logs (this may take a while)...")
                sparkService
                    .downloadAllLogs(targetStepId)
                    .getOrElse { error ->
                        error(error.message ?: "Failed to download logs")
                    }
            } else {
                sparkService
                    .downloadStepLogs(clusterInfo.clusterId, targetStepId)
                    .getOrElse { error ->
                        error(error.message ?: "Failed to download step logs")
                    }
            }

        outputHandler.handleMessage("Logs saved to: $logsDir")
    }

    /**
     * Gets the step ID of the most recent job on the cluster.
     *
     * @param clusterId The EMR cluster ID
     * @return The step ID of the most recent job
     * @throws IllegalStateException if no jobs are found
     */
    private fun getMostRecentStepId(clusterId: String): String {
        val jobs =
            sparkService
                .listJobs(clusterId, limit = 1)
                .getOrElse { error ->
                    error(error.message ?: "Failed to list jobs")
                }

        if (jobs.isEmpty()) {
            error("No jobs found on cluster $clusterId")
        }

        return jobs.first().stepId
    }
}
