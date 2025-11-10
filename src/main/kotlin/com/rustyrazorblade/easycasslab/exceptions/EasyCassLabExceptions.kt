package com.rustyrazorblade.easycasslab.exceptions

/**
 * Base exception for all Easy Cass Lab specific exceptions
 */
open class EasyCassLabException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * Thrown when duplicate Cassandra versions are found in configuration
 */
class DuplicateVersionException(
    versions: Set<String>,
) : EasyCassLabException(
        "Duplicate Cassandra version(s) found: ${versions.joinToString(", ")}. " +
            "Please ensure each version is unique.",
    )

/**
 * Thrown when a configuration error occurs
 */
class ConfigurationException(
    message: String,
    cause: Throwable? = null,
) : EasyCassLabException(message, cause)

/**
 * Thrown when a Docker operation fails
 */
class DockerOperationException(
    message: String,
    cause: Throwable? = null,
) : EasyCassLabException(message, cause)

/**
 * Thrown when a command execution fails
 */
class CommandExecutionException(
    message: String,
    cause: Throwable? = null,
) : EasyCassLabException(message, cause)

/**
 * Thrown when SSH operations fail
 */
class SSHException(
    message: String,
    cause: Throwable? = null,
) : EasyCassLabException(message, cause)

/**
 * Thrown when Terraform operations fail
 */
class TerraformException(
    message: String,
    cause: Throwable? = null,
) : EasyCassLabException(message, cause)
