package com.rustyrazorblade.easydblab.kubernetes

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.fabric8.kubernetes.client.KubernetesClient
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.ByteArrayInputStream
import java.io.File

/**
 * Utility for applying Kubernetes manifests using typed loaders.
 *
 * Fabric8's serverSideApply() only works with typed resources. The generic
 * client.load() returns GenericKubernetesResource which cannot be cast to typed
 * classes and has no handler for serverSideApply(). This utility extracts the
 * kind from YAML and routes to the appropriate typed loader.
 *
 * See docs/fabric8-server-side-apply.md for details on this issue.
 */
object ManifestApplier {
    private val log = KotlinLogging.logger {}
    private val yamlMapper = ObjectMapper(YAMLFactory()).registerKotlinModule()

    /**
     * Apply a manifest file to the cluster using server-side apply.
     * Supports multi-document YAML files (documents separated by ---).
     *
     * @param client Kubernetes client
     * @param file Manifest file to apply
     * @throws IllegalStateException if the resource kind is not supported
     */
    @Suppress("TooGenericExceptionCaught")
    fun applyManifest(
        client: KubernetesClient,
        file: File,
    ) {
        val yamlContent = file.readText()
        val documents = parseYamlDocuments(yamlContent)

        log.info { "Processing ${file.name} with ${documents.size} document(s)" }

        for ((index, document) in documents.withIndex()) {
            val kind =
                document["kind"] as? String
                    ?: error("Document ${index + 1} in ${file.name} missing 'kind' field")

            @Suppress("UNCHECKED_CAST")
            val metadata = document["metadata"] as? Map<String, Any>
            val resourceName = metadata?.get("name") as? String ?: "unknown"

            val documentYaml = yamlMapper.writeValueAsString(document)

            log.info { "Applying document ${index + 1}/${documents.size}: $kind '$resourceName' from ${file.name}" }
            log.debug { "YAML content:\n$documentYaml" }

            try {
                applySingleDocument(client, documentYaml, kind)
                log.info { "Successfully applied $kind '$resourceName'" }
            } catch (e: Exception) {
                log.error(e) { "Failed to apply $kind '$resourceName': ${e.message}" }
                throw e
            }
        }
    }

    /**
     * Parse a YAML file that may contain multiple documents.
     * Returns a list of parsed documents as Maps.
     *
     * @param yamlContent YAML content (may contain multiple documents separated by ---)
     * @return List of parsed documents
     */
    @Suppress("UNCHECKED_CAST")
    private fun parseYamlDocuments(yamlContent: String): List<Map<String, Any>> {
        val factory = YAMLFactory()
        val parser = factory.createParser(yamlContent)
        val documents = mutableListOf<Map<String, Any>>()

        while (parser.nextToken() != null) {
            val doc = yamlMapper.readValue(parser, Map::class.java) as? Map<String, Any>
            if (doc != null) {
                documents.add(doc)
            }
        }
        return documents
    }

    /**
     * Apply YAML content to the cluster using server-side apply.
     * Supports multi-document YAML (documents separated by ---).
     *
     * @param client Kubernetes client
     * @param yamlContent YAML content to apply
     * @throws IllegalStateException if the resource kind is not supported
     */
    fun applyYaml(
        client: KubernetesClient,
        yamlContent: String,
    ) {
        val documents = parseYamlDocuments(yamlContent)

        for ((index, document) in documents.withIndex()) {
            val kind =
                document["kind"] as? String
                    ?: error("Document ${index + 1} missing 'kind' field")

            val documentYaml = yamlMapper.writeValueAsString(document)
            applySingleDocument(client, documentYaml, kind)
        }
    }

    private fun applySingleDocument(
        client: KubernetesClient,
        yamlContent: String,
        kind: String,
    ) {
        log.debug { "Loading $kind via typed loader" }
        ByteArrayInputStream(yamlContent.toByteArray()).use { stream ->
            when (kind) {
                "Namespace" -> applyNamespace(client, stream)
                "ConfigMap" -> applyConfigMap(client, stream)
                "Service" -> applyService(client, stream)
                "DaemonSet" -> applyDaemonSet(client, stream)
                "Deployment" -> applyDeployment(client, stream)
                "StatefulSet" -> applyStatefulSet(client, stream)
                "Secret" -> applySecret(client, stream)
                else -> error("Unsupported resource kind: $kind")
            }
        }
    }

    private fun applyNamespace(
        client: KubernetesClient,
        stream: ByteArrayInputStream,
    ) = client
        .namespaces()
        .load(stream)
        .forceConflicts()
        .serverSideApply()

    private fun applyConfigMap(
        client: KubernetesClient,
        stream: ByteArrayInputStream,
    ) = client
        .configMaps()
        .load(stream)
        .forceConflicts()
        .serverSideApply()

    private fun applyService(
        client: KubernetesClient,
        stream: ByteArrayInputStream,
    ) = client
        .services()
        .load(stream)
        .forceConflicts()
        .serverSideApply()

    private fun applyDaemonSet(
        client: KubernetesClient,
        stream: ByteArrayInputStream,
    ) = client
        .apps()
        .daemonSets()
        .load(stream)
        .forceConflicts()
        .serverSideApply()

    private fun applyDeployment(
        client: KubernetesClient,
        stream: ByteArrayInputStream,
    ) = client
        .apps()
        .deployments()
        .load(stream)
        .forceConflicts()
        .serverSideApply()

    private fun applyStatefulSet(
        client: KubernetesClient,
        stream: ByteArrayInputStream,
    ) = client
        .apps()
        .statefulSets()
        .load(stream)
        .forceConflicts()
        .serverSideApply()

    private fun applySecret(
        client: KubernetesClient,
        stream: ByteArrayInputStream,
    ) = client
        .secrets()
        .load(stream)
        .forceConflicts()
        .serverSideApply()
}
