package com.rustyrazorblade.easycasslab.services

import com.rustyrazorblade.easycasslab.configuration.Host
import com.rustyrazorblade.easycasslab.output.OutputHandler
import com.rustyrazorblade.easycasslab.providers.ssh.RemoteOperationsService
import io.github.oshai.kotlinlogging.KotlinLogging

private val log = KotlinLogging.logger {}

/**
 * Service for managing cassandra-sidecar lifecycle operations.
 *
 * This service encapsulates all cassandra-sidecar lifecycle logic (start, stop, restart)
 * that was previously scattered across multiple command classes. It provides
 * a centralized interface for sidecar operations on individual hosts.
 *
 * All operations return Result types for explicit error handling.
 */
interface SidecarService {
    /**
     * Starts the cassandra-sidecar service on the specified host.
     *
     * @param host The host where cassandra-sidecar should be started
     * @return Result indicating success or failure with exception details
     */
    fun start(host: Host): Result<Unit>

    /**
     * Stops the cassandra-sidecar service on the specified host.
     *
     * @param host The host where cassandra-sidecar should be stopped
     * @return Result indicating success or failure with exception details
     */
    fun stop(host: Host): Result<Unit>

    /**
     * Restarts the cassandra-sidecar service on the specified host.
     *
     * @param host The host where cassandra-sidecar should be restarted
     * @return Result indicating success or failure with exception details
     */
    fun restart(host: Host): Result<Unit>

    /**
     * Checks if cassandra-sidecar is currently running on the specified host.
     *
     * @param host The host to check
     * @return Result containing true if cassandra-sidecar is active, false if inactive
     */
    fun isRunning(host: Host): Result<Boolean>

    /**
     * Gets the systemd status output for cassandra-sidecar on the specified host.
     *
     * @param host The host to query
     * @return Result containing the status output text
     */
    fun getStatus(host: Host): Result<String>
}

/**
 * Default implementation of SidecarService using SSH for remote operations.
 *
 * This implementation uses systemctl for service management of the
 * cassandra-sidecar systemd service on remote Cassandra nodes.
 *
 * @property remoteOps Service for executing SSH commands on remote hosts
 * @property outputHandler Handler for user-facing output messages
 */
class DefaultSidecarService(
    private val remoteOps: RemoteOperationsService,
    private val outputHandler: OutputHandler,
) : SidecarService {
    override fun start(host: Host): Result<Unit> =
        runCatching {
            outputHandler.handleMessage("Starting cassandra-sidecar on ${host.alias}...")

            remoteOps.executeRemotely(
                host,
                "sudo systemctl start cassandra-sidecar",
            )

            log.info { "Successfully started cassandra-sidecar on ${host.alias}" }
        }

    override fun stop(host: Host): Result<Unit> =
        runCatching {
            outputHandler.handleMessage("Stopping cassandra-sidecar on ${host.alias}...")

            remoteOps.executeRemotely(
                host,
                "sudo systemctl stop cassandra-sidecar",
            )

            log.info { "Successfully stopped cassandra-sidecar on ${host.alias}" }
        }

    override fun restart(host: Host): Result<Unit> =
        runCatching {
            outputHandler.handleMessage("Restarting cassandra-sidecar on ${host.alias}...")

            remoteOps.executeRemotely(
                host,
                "sudo systemctl restart cassandra-sidecar",
            )

            log.info { "Successfully restarted cassandra-sidecar on ${host.alias}" }
        }

    override fun isRunning(host: Host): Result<Boolean> =
        runCatching {
            log.debug { "Checking if cassandra-sidecar is running on ${host.alias}" }

            val response =
                remoteOps.executeRemotely(
                    host,
                    "sudo systemctl is-active cassandra-sidecar",
                    output = false,
                )

            // systemctl is-active returns "active" if the service is running
            // Any other response (inactive, failed, etc.) means it's not running normally
            val isActive = response.text.trim().equals("active", ignoreCase = true)

            log.debug {
                if (isActive) {
                    "cassandra-sidecar is active on ${host.alias}"
                } else {
                    "cassandra-sidecar is not active on ${host.alias}: ${response.text.trim()}"
                }
            }

            isActive
        }

    override fun getStatus(host: Host): Result<String> =
        runCatching {
            log.debug { "Getting status of cassandra-sidecar on ${host.alias}" }

            val response =
                remoteOps.executeRemotely(
                    host,
                    "sudo systemctl status cassandra-sidecar",
                    output = false,
                )

            response.text
        }
}

/**
 * Exception thrown when a cassandra-sidecar service operation fails.
 *
 * @property message Description of the failure
 * @property host The host where the operation failed
 * @property exitCode The exit code from the failed command (if applicable)
 * @property cause The underlying exception that caused this failure (if any)
 */
class SidecarServiceException(
    message: String,
    val host: Host? = null,
    val exitCode: Int? = null,
    cause: Throwable? = null,
) : Exception(message, cause)
