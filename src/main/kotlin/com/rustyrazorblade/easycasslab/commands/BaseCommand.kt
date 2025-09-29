package com.rustyrazorblade.easycasslab.commands

import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.di.TFStateProvider
import com.rustyrazorblade.easycasslab.output.OutputHandler
import com.rustyrazorblade.easycasslab.providers.ssh.RemoteOperationsService
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Base class for commands that need remote operations. Provides injected services for SSH
 * operations, Terraform state, and output handling.
 */
abstract class BaseCommand(val context: Context) : ICommand, KoinComponent {
    /** Injected RemoteOperationsService for SSH operations. */
    protected val remoteOps: RemoteOperationsService by inject()

    /**
     * Injected OutputHandler for console output. Command classes should use this instead of
     * println().
     */
    protected val outputHandler: OutputHandler by inject()

    /**
     * Injected TFStateProvider for Terraform state management. Use tfStateProvider.getDefault() to
     * get the current TFState.
     */
    protected val tfStateProvider: TFStateProvider by inject()

    /** Convenience property to get the default TFState. This replaces the old tfstate usage. */
    protected val tfstate by lazy { tfStateProvider.getDefault() }
}
