package com.rustyrazorblade.easydblab.providers.aws

import com.rustyrazorblade.easydblab.output.OutputHandler
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import software.amazon.awssdk.services.opensearch.OpenSearchClient
import software.amazon.awssdk.services.opensearch.model.ClusterConfig
import software.amazon.awssdk.services.opensearch.model.CreateDomainRequest
import software.amazon.awssdk.services.opensearch.model.CreateDomainResponse
import software.amazon.awssdk.services.opensearch.model.DeleteDomainRequest
import software.amazon.awssdk.services.opensearch.model.DeleteDomainResponse
import software.amazon.awssdk.services.opensearch.model.DescribeDomainRequest
import software.amazon.awssdk.services.opensearch.model.DescribeDomainResponse
import software.amazon.awssdk.services.opensearch.model.DomainStatus
import software.amazon.awssdk.services.opensearch.model.EBSOptions
import software.amazon.awssdk.services.opensearch.model.ServiceSoftwareOptions
import software.amazon.awssdk.services.opensearch.model.VolumeType

/**
 * Tests for OpenSearchService verifying domain lifecycle operations.
 */
internal class OpenSearchServiceTest {
    private lateinit var mockOpenSearchClient: OpenSearchClient
    private lateinit var mockOutputHandler: OutputHandler
    private lateinit var openSearchService: OpenSearchService

    @BeforeEach
    fun setUp() {
        mockOpenSearchClient = mock()
        mockOutputHandler = mock()
        openSearchService = OpenSearchService(mockOpenSearchClient, mockOutputHandler)
    }

    @Test
    fun `createDomain should create domain with correct configuration`() {
        val config =
            OpenSearchDomainConfig(
                domainName = "test-domain",
                instanceType = "t3.small.search",
                instanceCount = 1,
                ebsVolumeSize = 100,
                engineVersion = "OpenSearch_2.11",
                subnetId = "subnet-123",
                securityGroupIds = listOf("sg-123"),
                tags = mapOf("easy_cass_lab" to "1"),
            )

        val domainStatus =
            createMockDomainStatus(
                domainName = "test-domain",
                domainId = "123456789012/test-domain",
                endpoint = null,
                processing = true,
            )

        val response =
            CreateDomainResponse
                .builder()
                .domainStatus(domainStatus)
                .build()

        whenever(mockOpenSearchClient.createDomain(any<CreateDomainRequest>())).thenReturn(response)

        val result = openSearchService.createDomain(config)

        assertThat(result.domainName).isEqualTo("test-domain")
        assertThat(result.domainId).isEqualTo("123456789012/test-domain")
        assertThat(result.state).isEqualTo("Processing")
        verify(mockOpenSearchClient).createDomain(any<CreateDomainRequest>())
        verify(mockOutputHandler).handleMessage("Creating OpenSearch domain: test-domain...")
    }

    @Test
    fun `describeDomain should return domain status`() {
        val domainStatus =
            createMockDomainStatus(
                domainName = "test-domain",
                domainId = "123456789012/test-domain",
                endpoint = "search-test-domain.us-west-2.es.amazonaws.com",
                processing = false,
            )

        val response =
            DescribeDomainResponse
                .builder()
                .domainStatus(domainStatus)
                .build()

        whenever(mockOpenSearchClient.describeDomain(any<DescribeDomainRequest>())).thenReturn(response)

        val result = openSearchService.describeDomain("test-domain")

        assertThat(result.domainName).isEqualTo("test-domain")
        assertThat(result.endpoint).isEqualTo("search-test-domain.us-west-2.es.amazonaws.com")
        assertThat(result.state).isEqualTo("Active")
    }

    @Test
    fun `deleteDomain should delete the domain`() {
        val domainStatus =
            createMockDomainStatus(
                domainName = "test-domain",
                domainId = "123456789012/test-domain",
                endpoint = null,
                processing = true,
                deleted = true,
            )

        val response =
            DeleteDomainResponse
                .builder()
                .domainStatus(domainStatus)
                .build()

        whenever(mockOpenSearchClient.deleteDomain(any<DeleteDomainRequest>())).thenReturn(response)

        openSearchService.deleteDomain("test-domain")

        verify(mockOpenSearchClient).deleteDomain(any<DeleteDomainRequest>())
        verify(mockOutputHandler).handleMessage("Deleting OpenSearch domain: test-domain...")
    }

    @Test
    fun `isDomainActive should return true when domain is not processing`() {
        val domainStatus =
            createMockDomainStatus(
                domainName = "test-domain",
                domainId = "123456789012/test-domain",
                endpoint = "search-test-domain.us-west-2.es.amazonaws.com",
                processing = false,
            )

        val response =
            DescribeDomainResponse
                .builder()
                .domainStatus(domainStatus)
                .build()

        whenever(mockOpenSearchClient.describeDomain(any<DescribeDomainRequest>())).thenReturn(response)

        val result = openSearchService.isDomainActive("test-domain")

        assertThat(result).isTrue()
    }

    @Test
    fun `isDomainActive should return false when domain is processing`() {
        val domainStatus =
            createMockDomainStatus(
                domainName = "test-domain",
                domainId = "123456789012/test-domain",
                endpoint = null,
                processing = true,
            )

        val response =
            DescribeDomainResponse
                .builder()
                .domainStatus(domainStatus)
                .build()

        whenever(mockOpenSearchClient.describeDomain(any<DescribeDomainRequest>())).thenReturn(response)

        val result = openSearchService.isDomainActive("test-domain")

        assertThat(result).isFalse()
    }

    @Test
    fun `waitForDomainActive should return when domain becomes active`() {
        // First call: processing
        val processingStatus =
            createMockDomainStatus(
                domainName = "test-domain",
                domainId = "123456789012/test-domain",
                endpoint = null,
                processing = true,
            )

        // Second call: active
        val activeStatus =
            createMockDomainStatus(
                domainName = "test-domain",
                domainId = "123456789012/test-domain",
                endpoint = "search-test-domain.us-west-2.es.amazonaws.com",
                processing = false,
            )

        whenever(mockOpenSearchClient.describeDomain(any<DescribeDomainRequest>()))
            .thenReturn(DescribeDomainResponse.builder().domainStatus(processingStatus).build())
            .thenReturn(DescribeDomainResponse.builder().domainStatus(activeStatus).build())

        val result = openSearchService.waitForDomainActive("test-domain", pollIntervalMs = 10)

        assertThat(result.endpoint).isEqualTo("search-test-domain.us-west-2.es.amazonaws.com")
        assertThat(result.state).isEqualTo("Active")
    }

    @Test
    fun `waitForDomainActive should throw on timeout`() {
        val processingStatus =
            createMockDomainStatus(
                domainName = "test-domain",
                domainId = "123456789012/test-domain",
                endpoint = null,
                processing = true,
            )

        whenever(mockOpenSearchClient.describeDomain(any<DescribeDomainRequest>()))
            .thenReturn(DescribeDomainResponse.builder().domainStatus(processingStatus).build())

        assertThatThrownBy {
            openSearchService.waitForDomainActive("test-domain", timeoutMs = 50, pollIntervalMs = 10)
        }.isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("Timeout waiting for OpenSearch domain")
    }

    @Test
    fun `getDashboardsEndpoint should return correct URL`() {
        val endpoint = "search-test-domain.us-west-2.es.amazonaws.com"
        val dashboardsEndpoint = openSearchService.getDashboardsEndpoint(endpoint)

        assertThat(dashboardsEndpoint).isEqualTo("https://search-test-domain.us-west-2.es.amazonaws.com/_dashboards")
    }

    private fun createMockDomainStatus(
        domainName: String,
        domainId: String,
        endpoint: String?,
        processing: Boolean,
        deleted: Boolean = false,
    ): DomainStatus {
        val builder =
            DomainStatus
                .builder()
                .domainName(domainName)
                .domainId(domainId)
                .arn("arn:aws:es:us-west-2:123456789012:domain/$domainName")
                .created(true)
                .deleted(deleted)
                .processing(processing)
                .upgradeProcessing(false)
                .engineVersion("OpenSearch_2.11")
                .clusterConfig(
                    ClusterConfig
                        .builder()
                        .instanceType("t3.small.search")
                        .instanceCount(1)
                        .build(),
                ).ebsOptions(
                    EBSOptions
                        .builder()
                        .ebsEnabled(true)
                        .volumeType(VolumeType.GP3)
                        .volumeSize(100)
                        .build(),
                ).serviceSoftwareOptions(
                    ServiceSoftwareOptions
                        .builder()
                        .currentVersion("R20231023-P1")
                        .build(),
                )

        if (endpoint != null) {
            builder.endpoint(endpoint)
        }

        return builder.build()
    }
}
