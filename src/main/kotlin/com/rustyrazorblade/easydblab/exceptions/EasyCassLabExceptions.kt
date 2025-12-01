package com.rustyrazorblade.easydblab.exceptions

/**
 * Base exception for all Easy DB Lab specific exceptions
 */
open class EasyDBLabException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * Thrown when duplicate Cassandra versions are found in configuration
 */
class DuplicateVersionException(
    versions: Set<String>,
) : EasyDBLabException(
        "Duplicate Cassandra version(s) found: ${versions.joinToString(", ")}. " +
            "Please ensure each version is unique.",
    )

/**
 * Thrown when a configuration error occurs
 */
class ConfigurationException(
    message: String,
    cause: Throwable? = null,
) : EasyDBLabException(message, cause)

/**
 * Thrown when a Docker operation fails
 */
class DockerOperationException(
    message: String,
    cause: Throwable? = null,
) : EasyDBLabException(message, cause)

/**
 * Thrown when a command execution fails
 */
class CommandExecutionException(
    message: String,
    cause: Throwable? = null,
) : EasyDBLabException(message, cause)

/**
 * Thrown when SSH operations fail
 */
class SSHException(
    message: String,
    cause: Throwable? = null,
) : EasyDBLabException(message, cause)

/**
 * Thrown when an AWS operation times out waiting for resources
 */
class AwsTimeoutException(
    message: String,
    cause: Throwable? = null,
) : EasyDBLabException(message, cause)
