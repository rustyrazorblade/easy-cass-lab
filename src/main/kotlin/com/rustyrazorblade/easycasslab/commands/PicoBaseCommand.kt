package com.rustyrazorblade.easycasslab.commands

import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.configuration.ClusterStateManager
import com.rustyrazorblade.easycasslab.output.OutputHandler
import com.rustyrazorblade.easycasslab.providers.ssh.RemoteOperationsService
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Base class for PicoCLI commands that need remote operations.
 *
 * Provides injected services for SSH operations, cluster state, and output handling.
 * Most commands extend this class to get access to common infrastructure services.
 */
abstract class PicoBaseCommand(
    val context: Context,
) : PicoCommand,
    KoinComponent {
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
