package com.rustyrazorblade.easycasslab.commands

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.annotations.McpCommand
import com.rustyrazorblade.easycasslab.annotations.RequireProfileSetup
import com.rustyrazorblade.easycasslab.configuration.ClusterStateManager
import com.rustyrazorblade.easycasslab.configuration.User
import com.rustyrazorblade.easycasslab.configuration.s3Path
import com.rustyrazorblade.easycasslab.services.ObjectStore
import com.rustyrazorblade.easycasslab.services.SparkService
import org.koin.core.component.inject
import java.io.File

/**
 * Submit a Spark job to the provisioned EMR cluster.
 *
 * This command submits JAR-based Spark applications to the EMR cluster that was created during
 * environment initialization. It supports both S3 JAR paths and local JAR files (which are
 * automatically uploaded to S3).
 */
@McpCommand
@RequireProfileSetup
@Parameters(commandDescription = "Submit Spark job to EMR cluster")
class SparkSubmit(
    context: Context,
) : BaseCommand(context) {
    private val sparkService: SparkService by inject()
    private val objectStore: ObjectStore by inject()
    private val userConfig: User by inject()
    private val clusterStateManager: ClusterStateManager by inject()

    @Parameter(
        names = ["--jar"],
        description = "Path to JAR file (local path or s3://bucket/key)",
        required = true,
    )
    var jarPath: String = ""

    @Parameter(
        names = ["--main-class"],
        description = "Main class to execute",
        required = true,
    )
    var mainClass: String = ""

    @Parameter(
        names = ["--args"],
        description = "Arguments to pass to the Spark application",
        variableArity = true,
    )
    var jobArgs: List<String> = listOf()

    @Parameter(
        names = ["--wait"],
        description = "Wait for job completion",
    )
    var wait: Boolean = false

    @Parameter(
        names = ["--name"],
        description = "Job name (defaults to main class)",
    )
    var jobName: String? = null

    override fun execute() {
        // Validate cluster exists and is in valid state
        val clusterInfo =
            sparkService
                .validateCluster()
                .getOrElse { error ->
                    error(error.message ?: "Failed to validate EMR cluster")
                }

        // Determine JAR location (S3 or local)
        val s3JarPath =
            if (jarPath.startsWith("s3://")) {
                outputHandler.handleMessage("Using S3 JAR: $jarPath")
                jarPath
            } else {
                uploadJarToS3(jarPath)
            }

        // Submit job to EMR
        val stepId =
            sparkService
                .submitJob(
                    clusterId = clusterInfo.clusterId,
                    jarPath = s3JarPath,
                    mainClass = mainClass,
                    jobArgs = jobArgs,
                    jobName = jobName,
                ).getOrElse { exception ->
                    error(exception.message ?: "Failed to submit Spark job")
                }

        outputHandler.handleMessage("Submitted Spark job: $stepId to cluster ${clusterInfo.clusterId}")

        // Optionally wait for completion
        if (wait) {
            sparkService
                .waitForJobCompletion(clusterInfo.clusterId, stepId)
                .getOrElse { exception ->
                    error(exception.message ?: "Job failed")
                }
        } else {
            outputHandler.handleMessage(
                "Job submitted. Use 'aws emr describe-step --cluster-id ${clusterInfo.clusterId} --step-id $stepId' to check status.",
            )
        }
    }

    private fun uploadJarToS3(localPath: String): String {
        val localFile = File(localPath)
        require(localFile.exists()) { "JAR file does not exist: $localPath" }
        require(localPath.endsWith(".jar")) { "File must have .jar extension: $localPath" }

        // Load cluster state to get cluster-specific S3 path
        val clusterState = clusterStateManager.load()

        // Build cluster-specific S3 path using ClusterS3Path API
        val s3Path = clusterState.s3Path(userConfig)
        val jarPath = s3Path.sparkJars().resolve(localFile.name)

        // Upload using ObjectStore (handles retry logic and progress)
        val result = objectStore.uploadFile(localFile, jarPath, showProgress = true)

        return result.remotePath.toString()
    }
}
