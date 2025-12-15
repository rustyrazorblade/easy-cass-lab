package com.rustyrazorblade.easydblab.services

import com.github.ajalt.mordant.TermColors
import com.rustyrazorblade.easydblab.Constants
import com.rustyrazorblade.easydblab.Context
import com.rustyrazorblade.easydblab.annotations.RequireDocker
import com.rustyrazorblade.easydblab.annotations.RequireProfileSetup
import com.rustyrazorblade.easydblab.annotations.RequireSSHKey
import com.rustyrazorblade.easydblab.annotations.TriggerBackup
import com.rustyrazorblade.easydblab.commands.PicoCommand
import com.rustyrazorblade.easydblab.commands.SetupProfile
import com.rustyrazorblade.easydblab.configuration.ClusterStateManager
import com.rustyrazorblade.easydblab.configuration.UserConfigProvider
import com.rustyrazorblade.easydblab.output.OutputHandler
import com.rustyrazorblade.easydblab.providers.docker.DockerClientProvider
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.File
import java.util.ArrayDeque
import kotlin.system.exitProcess

/**
 * Service for executing commands with full lifecycle support.
 *
 * Provides two scheduling modes:
 * - [execute]: Execute a command immediately with full lifecycle. Use for mid-command delegation.
 * - [schedule]: Schedule a command to run after the current command's lifecycle completes.
 *
 * The full lifecycle includes:
 * 1. Requirement checks (@RequireProfileSetup, @RequireSSHKey, @RequireDocker)
 * 2. Pre-execute hooks (@PreExecute)
 * 3. Command execution
 * 4. Post-execute hooks (@PostExecute)
 * 5. Post-success actions (@TriggerBackup)
 */
interface CommandExecutor {
    /**
     * Execute a command immediately with full lifecycle.
     * Blocks until command completes. Use for mid-command delegation.
     *
     * Example usage:
     * ```
     * commandExecutor.execute { ConfigureAxonOps(context) }
     * ```
     *
     * @param commandFactory Lambda that creates the command instance
     * @return Exit code (0 for success, non-zero for failure)
     */
    fun <T : PicoCommand> execute(commandFactory: () -> T): Int

    /**
     * Schedule a command to run after the current command's lifecycle completes.
     * The scheduled command runs with its own full lifecycle.
     *
     * Example usage:
     * ```
     * commandExecutor.schedule { Up(context) }
     * ```
     *
     * @param commandFactory Lambda that creates the command instance
     */
    fun <T : PicoCommand> schedule(commandFactory: () -> T)
}

/**
 * Default implementation of CommandExecutor.
 *
 * Uses a thread-local queue to support nested command chains - each thread
 * maintains its own queue of scheduled commands.
 */
class DefaultCommandExecutor(
    private val context: Context,
    private val backupRestoreService: BackupRestoreService,
    private val clusterStateManager: ClusterStateManager,
    private val outputHandler: OutputHandler,
    private val userConfigProvider: UserConfigProvider,
    private val dockerClientProvider: DockerClientProvider,
) : CommandExecutor {
    // Thread-local queue for deferred command factories (supports nested command chains)
    private val scheduledQueue = ThreadLocal.withInitial { ArrayDeque<() -> PicoCommand>() }

    override fun <T : PicoCommand> execute(commandFactory: () -> T): Int {
        val command = commandFactory()
        return executeWithLifecycle(command)
    }

    override fun <T : PicoCommand> schedule(commandFactory: () -> T) {
        scheduledQueue.get().addLast(commandFactory)
    }

    /**
     * Execute a command with full lifecycle, then process any scheduled commands.
     * This is the entry point for top-level command execution (called by CommandLineParser).
     *
     * @param command The command to execute
     * @return Exit code (0 for success, non-zero for failure)
     */
    fun executeTopLevel(command: PicoCommand): Int {
        val exitCode = executeWithLifecycle(command)

        // Process scheduled commands (each with full lifecycle)
        // Stop on first failure
        while (scheduledQueue.get().isNotEmpty()) {
            val commandFactory = scheduledQueue.get().removeFirst()
            val scheduledCommand = commandFactory()
            val scheduledExitCode = executeWithLifecycle(scheduledCommand)
            if (scheduledExitCode != 0) {
                // Clear remaining scheduled commands on failure
                scheduledQueue.get().clear()
                return scheduledExitCode
            }
        }

        return exitCode
    }

    /**
     * Executes a command with the complete lifecycle:
     * 1. Check requirements
     * 2. Execute command (includes @PreExecute, execute(), @PostExecute via PicoCommand.call())
     * 3. Handle post-success actions
     */
    private fun executeWithLifecycle(command: PicoCommand): Int {
        // 1. Check requirements (may exit process on failure or run setup)
        checkRequirements(command)

        // 2. Execute with PicoCommand lifecycle (@PreExecute, execute(), @PostExecute)
        val exitCode =
            try {
                command.call()
            } catch (e: Exception) {
                log.error(e) { "Command execution failed" }
                outputHandler.handleError(e.message ?: "Command execution failed")
                Constants.ExitCodes.ERROR
            }

        // 3. Post-success actions
        if (exitCode == 0) {
            handlePostSuccessActions(command)
        }

        return exitCode
    }

    /**
     * Checks and enforces command requirements based on annotations.
     * May exit the process if requirements cannot be satisfied.
     */
    private fun checkRequirements(command: PicoCommand) {
        val annotations = command::class.annotations

        // Check if the command requires profile setup
        if (annotations.any { it is RequireProfileSetup }) {
            if (!userConfigProvider.isSetup()) {
                // Run setup command with full lifecycle
                executeWithLifecycle(SetupProfile(context))

                // Show message and exit
                with(TermColors()) {
                    outputHandler.handleMessage(green("\nYou can now run the command again."))
                }
                exitProcess(0)
            }
        }

        // Check if the command requires Docker
        if (annotations.any { it is RequireDocker }) {
            if (!checkDockerAvailability()) {
                outputHandler.handleError("Error: Docker is not available or not running.")
                outputHandler.handleError(
                    "Please ensure Docker is installed and running before executing this command.",
                )
                exitProcess(1)
            }
        }

        // Check if the command requires an SSH key
        if (annotations.any { it is RequireSSHKey }) {
            if (!checkSSHKeyAvailability()) {
                outputHandler.handleError("SSH key not found at ${userConfigProvider.sshKeyPath}")
                exitProcess(1)
            }
        }
    }

    /**
     * Handles post-success actions based on command annotations.
     */
    private fun handlePostSuccessActions(command: PicoCommand) {
        if (command::class.annotations.any { it is TriggerBackup }) {
            performIncrementalBackup()
        }
    }

    /**
     * Performs incremental backup of configuration files to S3.
     *
     * Computes hashes of all backup targets and uploads only files that have changed
     * since the last backup.
     */
    private fun performIncrementalBackup() {
        // Skip if no state file exists
        if (!clusterStateManager.exists()) {
            log.debug { "Skipping backup: no state file exists" }
            return
        }

        val state = clusterStateManager.load()

        // Skip if no S3 bucket configured
        if (state.s3Bucket.isNullOrBlank()) {
            log.debug { "Skipping backup: no S3 bucket configured" }
            return
        }

        backupRestoreService
            .backupChanged(context.workingDirectory.absolutePath, state)
            .onSuccess { result ->
                if (result.filesUploaded > 0) {
                    // Update state with new hashes
                    state.backupHashes = state.backupHashes + result.updatedHashes
                    clusterStateManager.save(state)
                    outputHandler.handleMessage("Backed up ${result.filesUploaded} changed configuration files to S3")
                }
            }.onFailure { e ->
                log.warn(e) { "Incremental backup failed" }
                outputHandler.handleMessage("Warning: Incremental backup failed: ${e.message}")
            }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun checkDockerAvailability(): Boolean =
        try {
            val dockerClient = dockerClientProvider.getDockerClient()
            // Try to list images as a simple health check
            dockerClient.listImages("", "")
            true
        } catch (e: Exception) {
            log.error(e) { "Docker availability check failed" }
            false
        }

    private fun checkSSHKeyAvailability(): Boolean = File(userConfigProvider.sshKeyPath).exists()

    companion object {
        private val log = KotlinLogging.logger {}
    }
}
