package com.rustyrazorblade.easydblab.services

import com.rustyrazorblade.easydblab.configuration.Host

/**
 * Service for configuring TLS on the container registry.
 *
 * This service handles the generation of self-signed certificates for the container registry
 * and the configuration of containerd on cluster nodes to trust those certificates.
 * It enables secure HTTPS communication between Kubernetes nodes and the private registry.
 *
 * The workflow is:
 * 1. Generate a self-signed certificate on the control node
 * 2. Upload the certificate to S3 for distribution
 * 3. Configure containerd on each node to trust the registry's certificate
 *
 * This must be executed BEFORE the registry pod starts to ensure all nodes can pull images.
 */
interface RegistryService {
    /**
     * Generates a self-signed TLS certificate on the control node and uploads it to S3.
     *
     * The certificate is generated with the control node's private IP as the Subject Alternative Name,
     * allowing other nodes to connect to the registry over HTTPS using the private IP address.
     *
     * @param controlHost The control node where the certificate will be generated
     * @param s3Bucket The S3 bucket name where the certificate will be uploaded
     */
    fun generateAndUploadCert(
        controlHost: Host,
        s3Bucket: String,
    )

    /**
     * Configures containerd on a node to trust the registry's TLS certificate.
     *
     * Downloads the CA certificate from S3 and configures containerd's certificate directory
     * to trust connections to the specified registry host. Restarts containerd to apply changes.
     *
     * @param host The node to configure
     * @param registryHost The private IP address of the registry (control node)
     * @param s3Bucket The S3 bucket containing the CA certificate
     */
    fun configureTlsOnNode(
        host: Host,
        registryHost: String,
        s3Bucket: String,
    )
}
