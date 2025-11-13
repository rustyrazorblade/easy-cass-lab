package com.rustyrazorblade.easycasslab.services

import com.rustyrazorblade.easycasslab.output.OutputHandler
import com.rustyrazorblade.easycasslab.providers.ssh.RemoteOperationsService
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * Service for managing cassandra-easy-stress lifecycle operations.
 *
 * This service encapsulates all cassandra-easy-stress lifecycle logic (start, stop, restart)
 * that was previously scattered across multiple command classes. It provides
 * a centralized interface for stress testing operations on individual hosts.
 *
 * All operations return Result types for explicit error handling.
 */
interface EasyStressService : SystemDServiceManager {
    // Interface now extends SystemDServiceManager for common lifecycle operations
    // All methods are inherited from the parent interface
}

/**
 * Default implementation of EasyStressService using SSH for remote operations.
 *
 * This implementation extends AbstractSystemDServiceManager to leverage common
 * systemd service management functionality, eliminating code duplication.
 *
 * @property remoteOps Service for executing SSH commands on remote hosts
 * @property outputHandler Handler for user-facing output messages
 */
class DefaultEasyStressService(
    remoteOps: RemoteOperationsService,
    outputHandler: OutputHandler,
) : AbstractSystemDServiceManager("cassandra-easy-stress", remoteOps, outputHandler),
    EasyStressService {
    override val log: KLogger = KotlinLogging.logger {}
}
