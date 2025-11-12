package com.rustyrazorblade.easycasslab.services

import com.rustyrazorblade.easycasslab.configuration.Host
import com.rustyrazorblade.easycasslab.output.OutputHandler
import com.rustyrazorblade.easycasslab.providers.ssh.RemoteOperationsService
import io.github.oshai.kotlinlogging.KotlinLogging

private val log = KotlinLogging.logger {}

/**
 * Service for managing Cassandra database lifecycle operations.
 *
 * This service encapsulates all Cassandra lifecycle logic (start, stop, restart)
 * that was previously scattered across multiple command classes. It provides
 * a centralized interface for Cassandra operations on individual hosts.
 *
 * All operations return Result types for explicit error handling.
 */
interface CassandraService {
    /**
     * Starts the Cassandra service on the specified host.
     *
     * @param host The host where Cassandra should be started
     * @param wait If true (default), waits for the node to reach UP/NORMAL status after starting
     * @return Result indicating success or failure with exception details
     */
    fun start(
        host: Host,
        wait: Boolean = true,
    ): Result<Unit>

    /**
     * Stops the Cassandra service on the specified host.
     *
     * @param host The host where Cassandra should be stopped
     * @return Result indicating success or failure with exception details
     */
    fun stop(host: Host): Result<Unit>

    /**
     * Restarts the Cassandra service on the specified host and waits for it to be ready.
     *
     * @param host The host where Cassandra should be restarted
     * @return Result indicating success or failure with exception details
     */
    fun restart(host: Host): Result<Unit>

    /**
     * Waits for Cassandra to reach UP/NORMAL status on the specified host.
     *
     * This method blocks until the node reports UP/NORMAL status or times out.
     *
     * @param host The host to wait for
     * @return Result indicating success (node is up) or failure (timeout/error)
     */
    fun waitForUpNormal(host: Host): Result<Unit>

    /**
     * Checks if Cassandra is currently running on the specified host.
     *
     * @param host The host to check
     * @return Result containing true if Cassandra is active, false if inactive
     */
    fun isRunning(host: Host): Result<Boolean>
}

/**
 * Default implementation of CassandraService using SSH for remote operations.
 *
 * This implementation uses systemctl for service management and relies on
 * provisioned scripts (wait-for-up-normal, restart-cassandra-and-wait) on
 * the remote hosts.
 *
 * @property remoteOps Service for executing SSH commands on remote hosts
 * @property outputHandler Handler for user-facing output messages
 */
class DefaultCassandraService(
    private val remoteOps: RemoteOperationsService,
    private val outputHandler: OutputHandler,
) : CassandraService {
    override fun start(
        host: Host,
        wait: Boolean,
    ): Result<Unit> =
        runCatching {
            outputHandler.handleMessage("Starting Cassandra on ${host.alias}...")

            remoteOps.executeRemotely(
                host,
                "sudo systemctl start cassandra",
            )

            if (wait) {
                outputHandler.handleMessage("Cassandra started, waiting for ${host.alias} to become UP/NORMAL...")
                remoteOps.executeRemotely(
                    host,
                    "sudo wait-for-up-normal",
                )
                log.info { "${host.alias} is now UP/NORMAL" }
            } else {
                log.info { "Successfully started Cassandra on ${host.alias} (not waiting for UP/NORMAL)" }
            }
        }

    override fun stop(host: Host): Result<Unit> =
        runCatching {
            outputHandler.handleMessage("Stopping Cassandra on ${host.alias}...")

            remoteOps.executeRemotely(
                host,
                "sudo systemctl stop cassandra",
            )

            log.info { "Successfully stopped Cassandra on ${host.alias}" }
        }

    override fun restart(host: Host): Result<Unit> =
        runCatching {
            outputHandler.handleMessage("Restarting Cassandra on ${host.alias}...")

            remoteOps.executeRemotely(
                host,
                "/usr/local/bin/restart-cassandra-and-wait",
            )

            log.info { "Successfully restarted Cassandra on ${host.alias}" }
        }

    override fun waitForUpNormal(host: Host): Result<Unit> =
        runCatching {
            outputHandler.handleMessage("Waiting for ${host.alias} to become UP/NORMAL...")

            remoteOps.executeRemotely(
                host,
                "sudo wait-for-up-normal",
            )

            log.info { "${host.alias} is now UP/NORMAL" }
        }

    override fun isRunning(host: Host): Result<Boolean> =
        runCatching {
            log.debug { "Checking if Cassandra is running on ${host.alias}" }

            val response =
                remoteOps.executeRemotely(
                    host,
                    "sudo systemctl is-active cassandra",
                    output = false,
                )

            // systemctl is-active returns "active" if the service is running
            // Any other response (inactive, failed, etc.) means it's not running normally
            val isActive = response.text.trim().equals("active", ignoreCase = true)

            log.debug {
                if (isActive) {
                    "Cassandra is active on ${host.alias}"
                } else {
                    "Cassandra is not active on ${host.alias}: ${response.text.trim()}"
                }
            }

            isActive
        }
}

/**
 * Exception thrown when a Cassandra service operation fails.
 *
 * @property message Description of the failure
 * @property host The host where the operation failed
 * @property exitCode The exit code from the failed command (if applicable)
 * @property cause The underlying exception that caused this failure (if any)
 */
class CassandraServiceException(
    message: String,
    val host: Host? = null,
    val exitCode: Int? = null,
    cause: Throwable? = null,
) : Exception(message, cause)
