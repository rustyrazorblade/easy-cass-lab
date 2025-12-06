package com.rustyrazorblade.easydblab.providers.aws

import com.rustyrazorblade.easydblab.Constants
import com.rustyrazorblade.easydblab.output.OutputHandler
import io.github.oshai.kotlinlogging.KotlinLogging
import software.amazon.awssdk.services.opensearch.OpenSearchClient
import software.amazon.awssdk.services.opensearch.model.ClusterConfig
import software.amazon.awssdk.services.opensearch.model.CreateDomainRequest
import software.amazon.awssdk.services.opensearch.model.DeleteDomainRequest
import software.amazon.awssdk.services.opensearch.model.DescribeDomainRequest
import software.amazon.awssdk.services.opensearch.model.DomainEndpointOptions
import software.amazon.awssdk.services.opensearch.model.EBSOptions
import software.amazon.awssdk.services.opensearch.model.EncryptionAtRestOptions
import software.amazon.awssdk.services.opensearch.model.ListDomainNamesRequest
import software.amazon.awssdk.services.opensearch.model.NodeToNodeEncryptionOptions
import software.amazon.awssdk.services.opensearch.model.OpenSearchPartitionInstanceType
import software.amazon.awssdk.services.opensearch.model.Tag
import software.amazon.awssdk.services.opensearch.model.VPCOptions
import software.amazon.awssdk.services.opensearch.model.VolumeType

/**
 * Configuration for creating an OpenSearch domain.
 *
 * @property domainName Unique name for the OpenSearch domain (3-28 characters, lowercase, numbers, hyphens)
 * @property instanceType OpenSearch instance type (e.g., "t3.small.search", "r5.large.search")
 * @property instanceCount Number of data nodes in the cluster
 * @property ebsVolumeSize Size of EBS volume in GB for each data node
 * @property engineVersion OpenSearch engine version (e.g., "OpenSearch_2.11")
 * @property subnetId VPC subnet ID for the domain (required for VPC deployment)
 * @property securityGroupIds Security group IDs for network access control
 * @property tags Resource tags for the domain
 */
data class OpenSearchDomainConfig(
    val domainName: String,
    val instanceType: String = Constants.OpenSearch.DEFAULT_INSTANCE_TYPE,
    val instanceCount: Int = Constants.OpenSearch.DEFAULT_INSTANCE_COUNT,
    val ebsVolumeSize: Int = Constants.OpenSearch.DEFAULT_EBS_SIZE_GB,
    val engineVersion: String = "OpenSearch_${Constants.OpenSearch.DEFAULT_VERSION}",
    val subnetId: String,
    val securityGroupIds: List<String>,
    val tags: Map<String, String> = emptyMap(),
)

/**
 * Result of an OpenSearch domain operation.
 *
 * @property domainName The domain name
 * @property domainId AWS-assigned domain ID
 * @property endpoint The domain REST API endpoint (null while creating)
 * @property dashboardsEndpoint URL to OpenSearch Dashboards UI
 * @property state Current domain state ("Processing", "Active", "Deleted")
 */
data class OpenSearchDomainResult(
    val domainName: String,
    val domainId: String,
    val endpoint: String? = null,
    val dashboardsEndpoint: String? = null,
    val state: String,
)

/**
 * Service for creating and managing AWS OpenSearch domains.
 *
 * This service handles the lifecycle of OpenSearch domains including creation,
 * status monitoring, and deletion. It follows the same patterns as EMRService
 * for consistency.
 *
 * OpenSearch domains typically take 10-30 minutes to create. The service provides
 * polling methods to wait for domain activation.
 */
class OpenSearchService(
    private val openSearchClient: OpenSearchClient,
    private val outputHandler: OutputHandler,
) {
    companion object {
        private val log = KotlinLogging.logger {}
    }

    /**
     * Creates a new OpenSearch domain with the specified configuration.
     *
     * The domain will be created in VPC mode with encryption at rest and
     * node-to-node encryption enabled for security.
     *
     * @param config Domain configuration
     * @return Result containing domain ID and initial status
     */
    fun createDomain(config: OpenSearchDomainConfig): OpenSearchDomainResult {
        log.info { "Creating OpenSearch domain: ${config.domainName}" }
        outputHandler.handleMessage("Creating OpenSearch domain: ${config.domainName}...")

        val tags =
            config.tags.map { (key, value) ->
                Tag
                    .builder()
                    .key(key)
                    .value(value)
                    .build()
            }

        val clusterConfig =
            ClusterConfig
                .builder()
                .instanceType(OpenSearchPartitionInstanceType.fromValue(config.instanceType))
                .instanceCount(config.instanceCount)
                .dedicatedMasterEnabled(false)
                .zoneAwarenessEnabled(false)
                .build()

        val ebsOptions =
            EBSOptions
                .builder()
                .ebsEnabled(true)
                .volumeType(VolumeType.GP3)
                .volumeSize(config.ebsVolumeSize)
                .build()

        val vpcOptions =
            VPCOptions
                .builder()
                .subnetIds(config.subnetId)
                .securityGroupIds(config.securityGroupIds)
                .build()

        val encryptionAtRest =
            EncryptionAtRestOptions
                .builder()
                .enabled(true)
                .build()

        val nodeToNode =
            NodeToNodeEncryptionOptions
                .builder()
                .enabled(true)
                .build()

        val domainEndpointOptions =
            DomainEndpointOptions
                .builder()
                .enforceHTTPS(true)
                .build()

        val request =
            CreateDomainRequest
                .builder()
                .domainName(config.domainName)
                .engineVersion(config.engineVersion)
                .clusterConfig(clusterConfig)
                .ebsOptions(ebsOptions)
                .vpcOptions(vpcOptions)
                .encryptionAtRestOptions(encryptionAtRest)
                .nodeToNodeEncryptionOptions(nodeToNode)
                .domainEndpointOptions(domainEndpointOptions)
                .tagList(tags)
                .build()

        val response =
            RetryUtil.withAwsRetry("create-opensearch-domain") {
                openSearchClient.createDomain(request)
            }

        val domainStatus = response.domainStatus()

        log.info { "OpenSearch domain creation initiated: ${domainStatus.domainId()}" }
        outputHandler.handleMessage("OpenSearch domain initiated: ${domainStatus.domainName()}")

        return OpenSearchDomainResult(
            domainName = domainStatus.domainName(),
            domainId = domainStatus.domainId(),
            endpoint = domainStatus.endpoint(),
            dashboardsEndpoint = domainStatus.endpoint()?.let { getDashboardsEndpoint(it) },
            state = if (domainStatus.processing()) "Processing" else "Active",
        )
    }

    /**
     * Gets the current status of an OpenSearch domain.
     *
     * @param domainName The domain name
     * @return Current domain status
     */
    fun describeDomain(domainName: String): OpenSearchDomainResult {
        val request =
            DescribeDomainRequest
                .builder()
                .domainName(domainName)
                .build()

        val response =
            RetryUtil.withAwsRetry("describe-opensearch-domain") {
                openSearchClient.describeDomain(request)
            }

        val domainStatus = response.domainStatus()

        val state =
            when {
                domainStatus.deleted() -> "Deleted"
                domainStatus.processing() -> "Processing"
                else -> "Active"
            }

        return OpenSearchDomainResult(
            domainName = domainStatus.domainName(),
            domainId = domainStatus.domainId(),
            endpoint = domainStatus.endpoint(),
            dashboardsEndpoint = domainStatus.endpoint()?.let { getDashboardsEndpoint(it) },
            state = state,
        )
    }

    /**
     * Deletes an OpenSearch domain.
     *
     * @param domainName The domain name to delete
     */
    fun deleteDomain(domainName: String) {
        log.info { "Deleting OpenSearch domain: $domainName" }
        outputHandler.handleMessage("Deleting OpenSearch domain: $domainName...")

        val request =
            DeleteDomainRequest
                .builder()
                .domainName(domainName)
                .build()

        RetryUtil.withAwsRetry("delete-opensearch-domain") {
            openSearchClient.deleteDomain(request)
        }

        log.info { "OpenSearch domain deletion initiated: $domainName" }
    }

    /**
     * Checks if a domain is active (not processing).
     *
     * @param domainName The domain name
     * @return true if domain is active and ready for use
     */
    fun isDomainActive(domainName: String): Boolean {
        val result = describeDomain(domainName)
        return result.state == "Active"
    }

    /**
     * Waits for an OpenSearch domain to become active.
     *
     * OpenSearch domains typically take 10-30 minutes to create. This method
     * polls the domain status until it becomes active or times out.
     *
     * @param domainName The domain name
     * @param timeoutMs Maximum time to wait in milliseconds (default: 45 minutes)
     * @param pollIntervalMs Interval between status checks (default: 30 seconds)
     * @return Domain result with endpoint information
     * @throws IllegalStateException if timeout is exceeded
     */
    fun waitForDomainActive(
        domainName: String,
        timeoutMs: Long = Constants.OpenSearch.MAX_POLL_TIMEOUT_MS,
        pollIntervalMs: Long = Constants.OpenSearch.POLL_INTERVAL_MS,
    ): OpenSearchDomainResult {
        log.info { "Waiting for OpenSearch domain $domainName to become active..." }
        outputHandler.handleMessage("Waiting for OpenSearch domain to become active (this may take 10-30 minutes)...")

        val startTime = System.currentTimeMillis()
        var pollCount = 0

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            val result = describeDomain(domainName)
            pollCount++

            when (result.state) {
                "Active" -> {
                    log.info { "OpenSearch domain $domainName is active" }
                    outputHandler.handleMessage("OpenSearch domain is ready")
                    return result
                }
                "Deleted" -> {
                    error("OpenSearch domain $domainName was deleted")
                }
                "Processing" -> {
                    // Log every LOG_INTERVAL_POLLS to reduce noise
                    if (pollCount % Constants.OpenSearch.LOG_INTERVAL_POLLS == 0) {
                        val elapsedMinutes = (System.currentTimeMillis() - startTime) / Constants.Time.MILLIS_PER_MINUTE
                        outputHandler.handleMessage("Domain still processing... ($elapsedMinutes minutes elapsed)")
                    }
                }
            }

            Thread.sleep(pollIntervalMs)
        }

        error("Timeout waiting for OpenSearch domain $domainName to become active after ${timeoutMs}ms")
    }

    /**
     * Waits for an OpenSearch domain to be fully deleted.
     *
     * OpenSearch domains take 10-20+ minutes to fully delete. This method
     * polls the domain status until it no longer exists or times out.
     *
     * @param domainName The domain name
     * @param timeoutMs Maximum time to wait in milliseconds (default: 45 minutes)
     * @param pollIntervalMs Interval between status checks (default: 30 seconds)
     * @throws IllegalStateException if timeout is exceeded
     */
    fun waitForDomainDeleted(
        domainName: String,
        timeoutMs: Long = Constants.OpenSearch.MAX_POLL_TIMEOUT_MS,
        pollIntervalMs: Long = Constants.OpenSearch.POLL_INTERVAL_MS,
    ) {
        log.info { "Waiting for OpenSearch domain $domainName to be deleted..." }
        outputHandler.handleMessage("Waiting for OpenSearch domain $domainName to be deleted (this may take 10-20 minutes)...")

        val startTime = System.currentTimeMillis()
        var pollCount = 0

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            pollCount++

            try {
                val result = describeDomain(domainName)
                when (result.state) {
                    "Deleted" -> {
                        log.info { "OpenSearch domain $domainName is deleted" }
                        outputHandler.handleMessage("OpenSearch domain $domainName deleted")
                        return
                    }
                    else -> {
                        if (pollCount % Constants.OpenSearch.LOG_INTERVAL_POLLS == 0) {
                            val elapsedMinutes =
                                (System.currentTimeMillis() - startTime) / Constants.Time.MILLIS_PER_MINUTE
                            outputHandler.handleMessage(
                                "Domain still deleting... ($elapsedMinutes minutes elapsed)",
                            )
                        }
                    }
                }
            } catch (e: software.amazon.awssdk.services.opensearch.model.ResourceNotFoundException) {
                // Domain no longer exists - deletion complete
                log.info { "OpenSearch domain $domainName no longer exists (deleted)" }
                outputHandler.handleMessage("OpenSearch domain $domainName deleted")
                return
            }

            Thread.sleep(pollIntervalMs)
        }

        error("Timeout waiting for OpenSearch domain $domainName to be deleted after ${timeoutMs}ms")
    }

    /**
     * Constructs the OpenSearch Dashboards URL from the domain endpoint.
     *
     * @param endpoint The domain REST API endpoint
     * @return Full URL to OpenSearch Dashboards
     */
    fun getDashboardsEndpoint(endpoint: String): String = "https://$endpoint/_dashboards"

    /**
     * Finds OpenSearch domains deployed in the specified VPC subnet.
     *
     * Iterates through all domains in the account and checks if they are
     * deployed in any of the given subnet IDs.
     *
     * @param subnetIds List of subnet IDs to search for domains in
     * @return List of domain names deployed in the specified subnets
     */
    fun findDomainsInVpc(subnetIds: List<String>): List<String> {
        if (subnetIds.isEmpty()) {
            return emptyList()
        }

        log.info { "Finding OpenSearch domains in subnets: $subnetIds" }

        // List all domain names in the account
        val listRequest = ListDomainNamesRequest.builder().build()
        val domainNames =
            RetryUtil.withAwsRetry("list-opensearch-domains") {
                openSearchClient.listDomainNames(listRequest).domainNames().map { it.domainName() }
            }

        if (domainNames.isEmpty()) {
            return emptyList()
        }

        // Check each domain to see if it's in one of our subnets
        val domainsInVpc = mutableListOf<String>()
        for (domainName in domainNames) {
            try {
                val describeRequest =
                    DescribeDomainRequest
                        .builder()
                        .domainName(domainName)
                        .build()

                val domain =
                    RetryUtil.withAwsRetry("describe-opensearch-domain") {
                        openSearchClient.describeDomain(describeRequest).domainStatus()
                    }

                // Check if the domain's VPC config includes our subnets
                val domainSubnets = domain.vpcOptions()?.subnetIds() ?: emptyList()
                if (domainSubnets.any { it in subnetIds }) {
                    log.info { "Found OpenSearch domain $domainName in VPC" }
                    domainsInVpc.add(domainName)
                }
            } catch (e: Exception) {
                log.warn(e) { "Failed to describe domain $domainName, skipping" }
            }
        }

        log.info { "Found ${domainsInVpc.size} OpenSearch domains in specified subnets" }
        return domainsInVpc
    }
}
