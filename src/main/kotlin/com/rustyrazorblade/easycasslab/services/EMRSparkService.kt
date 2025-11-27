package com.rustyrazorblade.easycasslab.services

import com.rustyrazorblade.easycasslab.Constants
import com.rustyrazorblade.easycasslab.configuration.EMRClusterInfo
import com.rustyrazorblade.easycasslab.configuration.TFState
import com.rustyrazorblade.easycasslab.output.OutputHandler
import com.rustyrazorblade.easycasslab.providers.RetryUtil
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.resilience4j.retry.Retry
import software.amazon.awssdk.services.emr.EmrClient
import software.amazon.awssdk.services.emr.model.ActionOnFailure
import software.amazon.awssdk.services.emr.model.AddJobFlowStepsRequest
import software.amazon.awssdk.services.emr.model.DescribeStepRequest
import software.amazon.awssdk.services.emr.model.HadoopJarStepConfig
import software.amazon.awssdk.services.emr.model.StepConfig
import software.amazon.awssdk.services.emr.model.StepState

/**
 * Default implementation of SparkService using AWS EMR.
 *
 * This implementation uses AWS SDK's EmrClient to interact with EMR clusters.
 * It follows the established service pattern in the codebase with Result types
 * for error handling and retry logic using RetryUtil.
 *
 * ## Retry Configuration
 * - Maximum 3 attempts with exponential backoff (1s, 2s, 4s)
 * - Retries only on 5xx server errors (via RetryUtil)
 *
 * ## Polling Behavior
 * When waiting for job completion, this service uses a blocking poll loop:
 * - Poll interval: 5 seconds (configurable via Constants.EMR.POLL_INTERVAL_MS)
 * - Maximum wait time: 4 hours (configurable via Constants.EMR.MAX_POLL_TIMEOUT_MS)
 * - Status logging: Every 60 seconds (12 polls) to reduce output noise
 * - The polling is blocking - it holds the thread until job completion or timeout
 *
 * @property emrClient AWS EMR client for API operations
 * @property tfState Terraform state for cluster discovery
 * @property outputHandler Handler for user-facing output messages
 */
class EMRSparkService(
    private val emrClient: EmrClient,
    private val tfState: TFState,
    private val outputHandler: OutputHandler,
) : SparkService {
    private val log = KotlinLogging.logger {}

    companion object {
        private val VALID_CLUSTER_STATES = setOf("WAITING", "RUNNING")
        private val TERMINAL_JOB_STATES = setOf(StepState.COMPLETED, StepState.FAILED, StepState.CANCELLED)
    }

    override fun submitJob(
        clusterId: String,
        jarPath: String,
        mainClass: String,
        jobArgs: List<String>,
        jobName: String?,
    ): Result<String> =
        runCatching {
            val stepName = jobName ?: mainClass.split(".").last()

            val hadoopJarStep =
                HadoopJarStepConfig
                    .builder()
                    .jar(Constants.EMR.COMMAND_RUNNER_JAR)
                    .args(buildSparkSubmitArgs(jarPath, mainClass, jobArgs))
                    .build()

            val stepConfig =
                StepConfig
                    .builder()
                    .name(stepName)
                    .actionOnFailure(ActionOnFailure.CONTINUE)
                    .hadoopJarStep(hadoopJarStep)
                    .build()

            val request =
                AddJobFlowStepsRequest
                    .builder()
                    .jobFlowId(clusterId)
                    .steps(stepConfig)
                    .build()

            val stepId =
                executeWithRetry("emr-submit-job") {
                    val response = emrClient.addJobFlowSteps(request)
                    val stepIds = response.stepIds()
                    require(stepIds.isNotEmpty()) {
                        "EMR returned no step IDs for submitted job"
                    }
                    stepIds.first()
                }

            log.info { "Submitted Spark job: $stepId to cluster $clusterId" }
            stepId
        }

    override fun waitForJobCompletion(
        clusterId: String,
        stepId: String,
    ): Result<SparkService.JobStatus> =
        runCatching {
            outputHandler.handleMessage("Waiting for job completion...")

            val startTime = System.currentTimeMillis()
            var currentStatus: SparkService.JobStatus
            var pollCount = 0

            do {
                Thread.sleep(Constants.EMR.POLL_INTERVAL_MS)
                pollCount++

                // Check for timeout
                val elapsed = System.currentTimeMillis() - startTime
                if (elapsed > Constants.EMR.MAX_POLL_TIMEOUT_MS) {
                    val timeoutMinutes = Constants.EMR.MAX_POLL_TIMEOUT_MS / Constants.Time.MILLIS_PER_MINUTE
                    error("Job polling timed out after $timeoutMinutes minutes")
                }

                val statusResult = getJobStatus(clusterId, stepId)
                currentStatus = statusResult.getOrThrow()

                // Log less frequently to reduce noise (every 60 seconds at default 5s interval)
                if (pollCount % Constants.EMR.LOG_INTERVAL_POLLS == 0) {
                    outputHandler.handleMessage("Job state: ${currentStatus.state}")
                }
            } while (currentStatus.state !in TERMINAL_JOB_STATES)

            when (currentStatus.state) {
                StepState.COMPLETED -> {
                    outputHandler.handleMessage("Job completed successfully")
                    currentStatus
                }
                StepState.FAILED -> {
                    val errorMessage = "Job failed: ${currentStatus.failureDetails ?: "Unknown reason"}"
                    log.error { errorMessage }
                    error(errorMessage)
                }
                StepState.CANCELLED -> {
                    val errorMessage = "Job was cancelled"
                    log.warn { errorMessage }
                    error(errorMessage)
                }
                else -> {
                    val errorMessage = "Job ended in unexpected state: ${currentStatus.state}"
                    log.error { errorMessage }
                    error(errorMessage)
                }
            }
        }

    override fun getJobStatus(
        clusterId: String,
        stepId: String,
    ): Result<SparkService.JobStatus> =
        runCatching {
            val describeRequest =
                DescribeStepRequest
                    .builder()
                    .clusterId(clusterId)
                    .stepId(stepId)
                    .build()

            executeWithRetry("emr-describe-step") {
                val response = emrClient.describeStep(describeRequest)
                val status = response.step().status()

                SparkService.JobStatus(
                    state = status.state(),
                    stateChangeReason = status.stateChangeReason()?.message(),
                    failureDetails = status.failureDetails()?.message(),
                )
            }
        }

    override fun validateCluster(): Result<EMRClusterInfo> =
        runCatching {
            val clusterInfo =
                tfState.getEMRCluster()
                    ?: error(
                        "No EMR cluster found in Terraform state. Use --spark.enable during init to create an EMR cluster.",
                    )

            check(clusterInfo.state in VALID_CLUSTER_STATES) {
                "EMR cluster is in state '${clusterInfo.state}'. Expected one of: $VALID_CLUSTER_STATES"
            }

            log.info { "Validated EMR cluster ${clusterInfo.clusterId} in state ${clusterInfo.state}" }
            clusterInfo
        }

    /**
     * Builds the spark-submit command arguments for EMR.
     *
     * @param jarPath S3 path to the JAR file
     * @param mainClass Main class to execute
     * @param jobArgs Application arguments
     * @return List of command-line arguments for spark-submit
     */
    private fun buildSparkSubmitArgs(
        jarPath: String,
        mainClass: String,
        jobArgs: List<String>,
    ): List<String> = listOf(Constants.EMR.SPARK_SUBMIT_COMMAND, "--class", mainClass, jarPath) + jobArgs

    /**
     * Executes an EMR API operation with retry logic.
     *
     * Uses exponential backoff (1s, 2s, 4s) with up to 3 attempts.
     * Retries only on 5xx server errors.
     *
     * @param operationName Name for the retry instance (for logging/metrics)
     * @param operation The EMR API operation to execute
     * @return The result of the operation
     */
    private fun <T> executeWithRetry(
        operationName: String,
        operation: () -> T,
    ): T {
        val retryConfig = RetryUtil.createAwsRetryConfig<T>()
        val retry = Retry.of(operationName, retryConfig)
        return Retry.decorateSupplier(retry, operation).get()
    }
}
