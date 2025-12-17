package com.rustyrazorblade.easydblab.commands

import com.rustyrazorblade.easydblab.Context
import com.rustyrazorblade.easydblab.configuration.ClusterStateManager
import com.rustyrazorblade.easydblab.output.OutputHandler
import com.rustyrazorblade.easydblab.providers.ssh.RemoteOperationsService
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Base class for PicoCLI commands that need remote operations.
 *
 * Provides injected services for SSH operations, cluster state, and output handling.
 * Most commands extend this class to get access to common infrastructure services.
 *
 * All dependencies are injected via Koin, including Context. Commands are instantiated
 * by KoinCommandFactory which ensures proper dependency injection.
 */
abstract class PicoBaseCommand :
    PicoCommand,
    KoinComponent {
    /** Injected Context for accessing configuration and state directories. */
    protected val context: Context by inject()

    /** Injected RemoteOperationsService for SSH operations. */
    protected val remoteOps: RemoteOperationsService by inject()

    /**
     * Injected OutputHandler for console output. Command classes should use this instead of
     * println().
     */
    protected val outputHandler: OutputHandler by inject()

    /** Injected ClusterStateManager for cluster state management. */
    protected val clusterStateManager: ClusterStateManager by inject()

    /** Convenience property to get the current ClusterState. */
    protected val clusterState by lazy { clusterStateManager.load() }
}
