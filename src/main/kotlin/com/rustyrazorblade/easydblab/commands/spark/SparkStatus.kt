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
 * Check the status of a Spark job on the EMR cluster.
 *
 * This command retrieves the current status of a Spark job step. If no step ID
 * is provided, it defaults to checking the most recent job.
 *
 * Usage:
 * - `spark status` - Shows status of most recent job
 * - `spark status --step-id s-XXXXX` - Shows status of specific job
 * - `spark status --logs` - Shows status and downloads step logs (stdout, stderr)
 */
@McpCommand
@RequireProfileSetup
@Command(
    name = "status",
    description = ["Check status of a Spark job"],
)
class SparkStatus(
    context: Context,
) : PicoBaseCommand(context) {
    private val sparkService: SparkService by inject()

    @Option(
        names = ["--step-id"],
        description = ["EMR step ID (defaults to most recent job)"],
    )
    var stepId: String? = null

    @Option(
        names = ["--logs"],
        description = ["Download step logs (stdout, stderr)"],
    )
    var downloadLogs: Boolean = false

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

        outputHandler.publishMessage("Checking status for step: $targetStepId")

        // Get job status
        val jobStatus =
            sparkService
                .getJobStatus(clusterInfo.clusterId, targetStepId)
                .getOrElse { error ->
                    error(error.message ?: "Failed to get job status")
                }

        // Display status information
        outputHandler.publishMessage("Step ID: $targetStepId")
        outputHandler.publishMessage("State: ${jobStatus.state}")
        jobStatus.stateChangeReason?.let {
            outputHandler.publishMessage("Reason: $it")
        }
        jobStatus.failureDetails?.let {
            outputHandler.publishMessage("Failure Details: $it")
        }

        // Download step logs if requested
        if (downloadLogs) {
            val logsDir =
                sparkService
                    .downloadStepLogs(clusterInfo.clusterId, targetStepId)
                    .getOrElse { error ->
                        outputHandler.publishError("Failed to download logs: ${error.message}")
                        return
                    }
            outputHandler.publishMessage("Step logs saved to: $logsDir")
        }
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
