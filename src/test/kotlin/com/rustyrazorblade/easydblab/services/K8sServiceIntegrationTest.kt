package com.rustyrazorblade.easydblab.services

import com.rustyrazorblade.easydblab.kubernetes.ManifestApplier
import io.fabric8.kubernetes.client.Config
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientBuilder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestMethodOrder
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.k3s.K3sContainer
import org.testcontainers.utility.DockerImageName
import java.io.File

/**
 * Integration tests for K8s manifest application using TestContainers with K3s.
 *
 * These tests verify that all actual project manifests can be applied successfully
 * to a real K3s cluster, catching errors before production deployment.
 *
 * Note: All resources are deployed to the 'default' namespace.
 */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class K8sServiceIntegrationTest {
    companion object {
        private const val DEFAULT_NAMESPACE = "default"
        private const val K8S_MANIFEST_DIR = "src/main/resources/com/rustyrazorblade/easydblab/commands/k8s/core"

        @Container
        @JvmStatic
        val k3s: K3sContainer = K3sContainer(DockerImageName.parse("rancher/k3s:v1.30.6-k3s1"))
    }

    private lateinit var client: KubernetesClient

    /**
     * Resource types for K8s manifests
     */
    enum class ResourceType {
        SERVICE,
        CONFIGMAP,
        DAEMONSET,
        DEPLOYMENT,
    }

    /**
     * Test case definition for manifest testing
     */
    data class ManifestTestCase(
        val filename: String,
        val resourceType: ResourceType,
        val resourceName: String,
        val dataKey: String? = null,
    )

    /**
     * Ordered list of manifests to test (after namespace)
     */
    private val manifestTestCases =
        listOf(
            ManifestTestCase("20-otel-service.yaml", ResourceType.SERVICE, "otel-collector"),
            ManifestTestCase(
                "10-otel-configmap-control.yaml",
                ResourceType.CONFIGMAP,
                "otel-collector-config-control",
                "otel-collector-config.yaml",
            ),
            ManifestTestCase(
                "11-otel-configmap-workers.yaml",
                ResourceType.CONFIGMAP,
                "otel-collector-config-workers",
                "otel-collector-config.yaml",
            ),
            ManifestTestCase("30-otel-daemonset-control.yaml", ResourceType.DAEMONSET, "otel-collector-control"),
            ManifestTestCase("31-otel-daemonset-workers.yaml", ResourceType.DAEMONSET, "otel-collector-workers"),
            ManifestTestCase("12-prometheus-configmap.yaml", ResourceType.CONFIGMAP, "prometheus-config", "prometheus.yml"),
            ManifestTestCase("40-prometheus-deployment.yaml", ResourceType.DEPLOYMENT, "prometheus"),
            ManifestTestCase(
                "13-grafana-datasource-configmap.yaml",
                ResourceType.CONFIGMAP,
                "grafana-datasources",
                "datasources.yaml",
            ),
            ManifestTestCase(
                "14-grafana-dashboards-configmap.yaml",
                ResourceType.CONFIGMAP,
                "grafana-dashboards-config",
                "dashboards.yaml",
            ),
            ManifestTestCase("15-grafana-dashboard-system.yaml", ResourceType.CONFIGMAP, "grafana-dashboard-system"),
            ManifestTestCase("41-grafana-deployment.yaml", ResourceType.DEPLOYMENT, "grafana"),
            ManifestTestCase("42-registry-deployment.yaml", ResourceType.DEPLOYMENT, "registry"),
        )

    @BeforeAll
    fun setup() {
        val kubeconfig = k3s.kubeConfigYaml
        val config = Config.fromKubeconfig(kubeconfig)
        client =
            KubernetesClientBuilder()
                .withConfig(config)
                .build()
    }

    @Test
    @Order(1)
    fun `default namespace should exist`() {
        // The default namespace is pre-existing in K3s, so we just verify it exists
        val namespace = client.namespaces().withName(DEFAULT_NAMESPACE).get()
        assertThat(namespace)
            .withFailMessage("Namespace '$DEFAULT_NAMESPACE' does not exist")
            .isNotNull
    }

    @TestFactory
    @Order(2)
    fun `should apply all manifests`(): List<DynamicTest> =
        manifestTestCases.map { testCase ->
            DynamicTest.dynamicTest("apply ${testCase.filename}") {
                applyAndVerifyManifest(testCase)
            }
        }

    @Test
    @Order(3)
    fun `should have created all expected resources`() {
        // Final verification - check counts of all resource types
        val namespaces = client.namespaces().withName(DEFAULT_NAMESPACE).get()
        assertThat(namespaces).isNotNull

        val configMaps =
            client
                .configMaps()
                .inNamespace(DEFAULT_NAMESPACE)
                .list()
        assertThat(configMaps.items)
            .withFailMessage("Expected at least 6 ConfigMaps, found ${configMaps.items.size}")
            .hasSizeGreaterThanOrEqualTo(6)

        val deployments =
            client
                .apps()
                .deployments()
                .inNamespace(DEFAULT_NAMESPACE)
                .list()
        assertThat(deployments.items)
            .withFailMessage("Expected at least 3 Deployments (prometheus, grafana, registry)")
            .hasSizeGreaterThanOrEqualTo(3)

        val daemonSets =
            client
                .apps()
                .daemonSets()
                .inNamespace(DEFAULT_NAMESPACE)
                .list()
        assertThat(daemonSets.items)
            .withFailMessage("Expected at least 2 DaemonSets (otel-control, otel-workers)")
            .hasSizeGreaterThanOrEqualTo(2)

        val services =
            client
                .services()
                .inNamespace(DEFAULT_NAMESPACE)
                .list()
        // Only otel-collector has a Service; prometheus/grafana use hostNetwork
        // Note: default namespace also has kubernetes service
        assertThat(services.items)
            .withFailMessage("Expected at least 2 Services (kubernetes, otel-collector)")
            .hasSizeGreaterThanOrEqualTo(2)
    }

    /**
     * Apply a manifest and verify the expected resource was created.
     */
    private fun applyAndVerifyManifest(testCase: ManifestTestCase) {
        val manifestFile = File(K8S_MANIFEST_DIR, testCase.filename)
        assertThat(manifestFile.exists())
            .withFailMessage("Manifest file not found: ${manifestFile.absolutePath}")
            .isTrue()

        try {
            applyManifest(manifestFile)
        } catch (e: Exception) {
            throw AssertionError("Failed to apply manifest '${testCase.filename}': ${e.message}", e)
        }

        verifyResource(testCase)
    }

    /**
     * Verify a resource was created based on its type.
     */
    private fun verifyResource(testCase: ManifestTestCase) {
        when (testCase.resourceType) {
            ResourceType.SERVICE -> {
                val service =
                    client
                        .services()
                        .inNamespace(DEFAULT_NAMESPACE)
                        .withName(testCase.resourceName)
                        .get()
                assertThat(service)
                    .withFailMessage("Service '${testCase.resourceName}' was not created")
                    .isNotNull
            }
            ResourceType.CONFIGMAP -> {
                val configMap =
                    client
                        .configMaps()
                        .inNamespace(DEFAULT_NAMESPACE)
                        .withName(testCase.resourceName)
                        .get()
                assertThat(configMap)
                    .withFailMessage("ConfigMap '${testCase.resourceName}' was not created")
                    .isNotNull
                testCase.dataKey?.let { key ->
                    assertThat(configMap.data)
                        .withFailMessage("ConfigMap '${testCase.resourceName}' missing data key '$key'")
                        .containsKey(key)
                }
            }
            ResourceType.DAEMONSET -> {
                val daemonSet =
                    client
                        .apps()
                        .daemonSets()
                        .inNamespace(DEFAULT_NAMESPACE)
                        .withName(testCase.resourceName)
                        .get()
                assertThat(daemonSet)
                    .withFailMessage("DaemonSet '${testCase.resourceName}' was not created")
                    .isNotNull
            }
            ResourceType.DEPLOYMENT -> {
                val deployment =
                    client
                        .apps()
                        .deployments()
                        .inNamespace(DEFAULT_NAMESPACE)
                        .withName(testCase.resourceName)
                        .get()
                assertThat(deployment)
                    .withFailMessage("Deployment '${testCase.resourceName}' was not created")
                    .isNotNull
            }
        }
    }

    /**
     * Applies a manifest file using the shared ManifestApplier utility.
     */
    private fun applyManifest(file: File) {
        ManifestApplier.applyManifest(client, file)
    }
}
