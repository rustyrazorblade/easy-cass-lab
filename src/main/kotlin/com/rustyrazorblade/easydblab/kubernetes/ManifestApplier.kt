package com.rustyrazorblade.easydblab.kubernetes

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

    /**
     * Apply a manifest file to the cluster using server-side apply.
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
        val kind = extractKind(yamlContent)
        val resourceName = extractName(yamlContent)

        log.info { "Applying $kind resource '$resourceName' from ${file.name}" }
        log.debug { "YAML content:\n$yamlContent" }

        try {
            applyYaml(client, yamlContent, kind)
            log.info { "Successfully applied $kind '$resourceName'" }
        } catch (e: Exception) {
            log.error(e) { "Failed to apply $kind '$resourceName': ${e.message}" }
            throw e
        }
    }

    /**
     * Apply YAML content to the cluster using server-side apply.
     *
     * @param client Kubernetes client
     * @param yamlContent YAML content to apply
     * @throws IllegalStateException if the resource kind is not supported
     */
    fun applyYaml(
        client: KubernetesClient,
        yamlContent: String,
    ) {
        val kind = extractKind(yamlContent)
        applyYaml(client, yamlContent, kind)
    }

    private fun applyYaml(
        client: KubernetesClient,
        yamlContent: String,
        kind: String,
    ) {
        ByteArrayInputStream(yamlContent.toByteArray()).use { stream ->
            when (kind) {
                "Namespace" -> {
                    log.debug { "Loading Namespace via typed loader" }
                    client
                        .namespaces()
                        .load(stream)
                        .forceConflicts()
                        .serverSideApply()
                }
                "ConfigMap" -> {
                    log.debug { "Loading ConfigMap via typed loader" }
                    client
                        .configMaps()
                        .load(stream)
                        .forceConflicts()
                        .serverSideApply()
                }
                "Service" -> {
                    log.debug { "Loading Service via typed loader" }
                    client
                        .services()
                        .load(stream)
                        .forceConflicts()
                        .serverSideApply()
                }
                "DaemonSet" -> {
                    log.debug { "Loading DaemonSet via typed loader" }
                    client
                        .apps()
                        .daemonSets()
                        .load(stream)
                        .forceConflicts()
                        .serverSideApply()
                }
                "Deployment" -> {
                    log.debug { "Loading Deployment via typed loader" }
                    client
                        .apps()
                        .deployments()
                        .load(stream)
                        .forceConflicts()
                        .serverSideApply()
                }
                else -> throw IllegalStateException("Unsupported resource kind: $kind")
            }
        }
    }

    /**
     * Extract the kind field from a YAML manifest.
     *
     * @param yamlContent YAML content
     * @return The resource kind (e.g., "Namespace", "ConfigMap")
     * @throws IllegalStateException if kind cannot be determined
     */
    fun extractKind(yamlContent: String): String {
        val kindRegex = Regex("""^kind:\s*(\w+)""", RegexOption.MULTILINE)
        return kindRegex.find(yamlContent)?.groupValues?.get(1)
            ?: throw IllegalStateException("Could not determine resource kind from YAML")
    }

    /**
     * Extract the name field from a YAML manifest.
     *
     * @param yamlContent YAML content
     * @return The resource name, or "unknown" if not found
     */
    fun extractName(yamlContent: String): String {
        val nameRegex = Regex("""^\s*name:\s*(\S+)""", RegexOption.MULTILINE)
        return nameRegex.find(yamlContent)?.groupValues?.get(1) ?: "unknown"
    }
}
