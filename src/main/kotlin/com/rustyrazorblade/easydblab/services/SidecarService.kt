package com.rustyrazorblade.easydblab.services

import com.rustyrazorblade.easydblab.output.OutputHandler
import com.rustyrazorblade.easydblab.providers.ssh.RemoteOperationsService
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * Service for managing cassandra-sidecar lifecycle operations.
 *
 * This service encapsulates all cassandra-sidecar lifecycle logic (start, stop, restart)
 * that was previously scattered across multiple command classes. It provides
 * a centralized interface for sidecar operations on individual hosts.
 *
 * All operations return Result types for explicit error handling.
 */
interface SidecarService : SystemDServiceManager {
    // Interface now extends SystemDServiceManager for common lifecycle operations
    // All methods are inherited from the parent interface
}

/**
 * Default implementation of SidecarService using SSH for remote operations.
 *
 * This implementation extends AbstractSystemDServiceManager to leverage common
 * systemd service management functionality, eliminating code duplication.
 *
 * @property remoteOps Service for executing SSH commands on remote hosts
 * @property outputHandler Handler for user-facing output messages
 */
class DefaultSidecarService(
    remoteOps: RemoteOperationsService,
    outputHandler: OutputHandler,
) : AbstractSystemDServiceManager("cassandra-sidecar", remoteOps, outputHandler),
    SidecarService {
    override val log: KLogger = KotlinLogging.logger {}
}
