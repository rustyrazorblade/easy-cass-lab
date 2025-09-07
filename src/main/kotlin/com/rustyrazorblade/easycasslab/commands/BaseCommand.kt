package com.rustyrazorblade.easycasslab.commands

import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.output.OutputHandler
import com.rustyrazorblade.easycasslab.providers.ssh.RemoteOperationsService
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Base class for commands that need remote operations.
 * Provides injected RemoteOperationsService for SSH operations.
 */
abstract class BaseCommand(val context: Context) : ICommand, KoinComponent {
    /**
     * Injected RemoteOperationsService for SSH operations.
     */
    protected val remoteOps: RemoteOperationsService by inject()

    /**
     * Injected OutputHandler for console output.
     * Command classes should use this instead of println().
     */
    protected val outputHandler: OutputHandler by inject()
}
