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
 */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class K8sServiceIntegrationTest {
    companion object {
        private const val OBSERVABILITY_NAMESPACE = "observability"
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
            ManifestTestCase("otel-service.yaml", ResourceType.SERVICE, "otel-collector"),
            ManifestTestCase(
                "otel-configmap-control.yaml",
                ResourceType.CONFIGMAP,
                "otel-collector-config-control",
                "otel-collector-config.yaml",
            ),
            ManifestTestCase(
                "otel-configmap-workers.yaml",
                ResourceType.CONFIGMAP,
                "otel-collector-config-workers",
                "otel-collector-config.yaml",
            ),
            ManifestTestCase("otel-daemonset-control.yaml", ResourceType.DAEMONSET, "otel-collector-control"),
            ManifestTestCase("otel-daemonset-workers.yaml", ResourceType.DAEMONSET, "otel-collector-workers"),
            ManifestTestCase("prometheus-configmap.yaml", ResourceType.CONFIGMAP, "prometheus-config", "prometheus.yml"),
            ManifestTestCase("prometheus-deployment.yaml", ResourceType.DEPLOYMENT, "prometheus"),
            ManifestTestCase(
                "grafana-datasource-configmap.yaml",
                ResourceType.CONFIGMAP,
                "grafana-datasources",
                "datasources.yaml",
            ),
            ManifestTestCase(
                "grafana-dashboards-configmap.yaml",
                ResourceType.CONFIGMAP,
                "grafana-dashboards-config",
                "dashboards.yaml",
            ),
            ManifestTestCase("grafana-dashboard-system.yaml", ResourceType.CONFIGMAP, "grafana-dashboard-system"),
            ManifestTestCase("grafana-deployment.yaml", ResourceType.DEPLOYMENT, "grafana"),
            ManifestTestCase("registry-deployment.yaml", ResourceType.DEPLOYMENT, "registry"),
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
    fun `should apply namespace manifest first`() {
        val manifestFile = File(K8S_MANIFEST_DIR, "namespace.yaml")
        assertThat(manifestFile.exists())
            .withFailMessage("Manifest file not found: ${manifestFile.absolutePath}")
            .isTrue()

        applyManifest(manifestFile)

        val namespace = client.namespaces().withName(OBSERVABILITY_NAMESPACE).get()
        assertThat(namespace)
            .withFailMessage("Namespace '$OBSERVABILITY_NAMESPACE' was not created by namespace.yaml")
            .isNotNull
        assertThat(namespace.metadata.labels)
            .containsEntry("app.kubernetes.io/name", "observability")
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
        val namespaces = client.namespaces().withName(OBSERVABILITY_NAMESPACE).get()
        assertThat(namespaces).isNotNull

        val configMaps =
            client
                .configMaps()
                .inNamespace(OBSERVABILITY_NAMESPACE)
                .list()
        assertThat(configMaps.items)
            .withFailMessage("Expected at least 6 ConfigMaps, found ${configMaps.items.size}")
            .hasSizeGreaterThanOrEqualTo(6)

        val deployments =
            client
                .apps()
                .deployments()
                .inNamespace(OBSERVABILITY_NAMESPACE)
                .list()
        assertThat(deployments.items)
            .withFailMessage("Expected at least 3 Deployments (prometheus, grafana, registry)")
            .hasSizeGreaterThanOrEqualTo(3)

        val daemonSets =
            client
                .apps()
                .daemonSets()
                .inNamespace(OBSERVABILITY_NAMESPACE)
                .list()
        assertThat(daemonSets.items)
            .withFailMessage("Expected at least 2 DaemonSets (otel-control, otel-workers)")
            .hasSizeGreaterThanOrEqualTo(2)

        val services =
            client
                .services()
                .inNamespace(OBSERVABILITY_NAMESPACE)
                .list()
        // Only otel-collector has a Service; prometheus/grafana use hostNetwork
        assertThat(services.items)
            .withFailMessage("Expected at least 1 Service (otel-collector)")
            .hasSizeGreaterThanOrEqualTo(1)
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
                        .inNamespace(OBSERVABILITY_NAMESPACE)
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
                        .inNamespace(OBSERVABILITY_NAMESPACE)
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
                        .inNamespace(OBSERVABILITY_NAMESPACE)
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
                        .inNamespace(OBSERVABILITY_NAMESPACE)
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
