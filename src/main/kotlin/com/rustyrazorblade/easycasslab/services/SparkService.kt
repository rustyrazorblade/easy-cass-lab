package com.rustyrazorblade.easycasslab.services

import com.rustyrazorblade.easycasslab.configuration.EMRClusterInfo
import software.amazon.awssdk.services.emr.model.StepState

/**
 * Service for managing Spark job lifecycle on EMR clusters.
 *
 * This service encapsulates all EMR Spark operations including job submission,
 * monitoring, and status checking. It provides a centralized interface for
 * Spark operations that can be used by multiple commands.
 *
 * All operations return Result types for explicit error handling following
 * the established pattern in this codebase.
 *
 * Future implementations could support alternative Spark providers such as:
 * - LocalSparkService for local development
 * - DatabricksSparkService for cloud-agnostic deployments
 */
interface SparkService {
    /**
     * Submits a Spark job to the EMR cluster.
     *
     * @param clusterId The EMR cluster ID
     * @param jarPath S3 path to the JAR file (s3://bucket/key)
     * @param mainClass Main class to execute
     * @param jobArgs Arguments to pass to the Spark application
     * @param jobName Optional job name (defaults to main class)
     * @return Result containing the EMR step ID on success, or error on failure
     */
    fun submitJob(
        clusterId: String,
        jarPath: String,
        mainClass: String,
        jobArgs: List<String> = listOf(),
        jobName: String? = null,
    ): Result<String>

    /**
     * Waits for a Spark job to complete, polling EMR for status updates.
     *
     * This method blocks until the job reaches a terminal state (COMPLETED, FAILED, or CANCELLED).
     * It polls the EMR API periodically and provides progress updates via OutputHandler.
     *
     * @param clusterId The EMR cluster ID
     * @param stepId The EMR step ID to monitor
     * @return Result containing the final JobStatus on success, or error on failure
     */
    fun waitForJobCompletion(
        clusterId: String,
        stepId: String,
    ): Result<JobStatus>

    /**
     * Gets the current status of a Spark job.
     *
     * This is a non-blocking call that returns the current job state.
     *
     * @param clusterId The EMR cluster ID
     * @param stepId The EMR step ID
     * @return Result containing the current JobStatus, or error on failure
     */
    fun getJobStatus(
        clusterId: String,
        stepId: String,
    ): Result<JobStatus>

    /**
     * Validates that the EMR cluster exists and is in a valid state for job submission.
     *
     * Valid states are: WAITING, RUNNING
     *
     * @return Result containing EMRClusterInfo if cluster is valid, or error if not found or in invalid state
     */
    fun validateCluster(): Result<EMRClusterInfo>

    /**
     * Represents the status of a Spark job.
     *
     * @property state The current EMR step state
     * @property stateChangeReason Optional reason for state change (e.g., "User request")
     * @property failureDetails Optional details if the job failed
     */
    data class JobStatus(
        val state: StepState,
        val stateChangeReason: String? = null,
        val failureDetails: String? = null,
    )
}
