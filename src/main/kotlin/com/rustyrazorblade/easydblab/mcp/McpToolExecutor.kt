package com.rustyrazorblade.easydblab.mcp

import com.rustyrazorblade.easydblab.Constants
import com.rustyrazorblade.easydblab.commands.PicoCommand
import com.rustyrazorblade.easydblab.output.SubscribableOutputHandler
import io.github.oshai.kotlinlogging.KotlinLogging
import io.modelcontextprotocol.server.McpSyncServerExchange
import io.modelcontextprotocol.spec.McpSchema
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Executes MCP tools on a single-thread executor with progress notification support.
 *
 * This executor provides:
 * - **Queue**: Pending requests wait in FIFO order
 * - **Concurrency limit**: Only 1 command runs at a time
 * - **Progress streaming**: Output messages are sent as MCP progress notifications
 * - **Timeout handling**: Commands that exceed the timeout are cancelled
 *
 * The single-thread model ensures that concurrent MCP requests don't interfere with
 * each other and simplifies reasoning about system state during command execution.
 */
class McpToolExecutor : KoinComponent {
    companion object {
        private val log = KotlinLogging.logger {}
    }

    // Single thread executor: queue + concurrency limit of 1
    private val executor: ExecutorService =
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "mcp-tool-executor").apply { isDaemon = true }
        }

    private val outputHandler: SubscribableOutputHandler by inject()

    /**
     * Execute a tool and return the result.
     *
     * The command is executed on a background thread while progress notifications
     * are sent to the MCP client via the exchange. The progress notifier is subscribed
     * to the output handler during execution and unsubscribed when complete.
     *
     * Progress notifications are only sent if the client provided a progressToken.
     * This follows the MCP protocol specification where the client must opt-in.
     *
     * @param entry The MCP command entry to execute
     * @param arguments Optional arguments to pass to the command
     * @param progressToken The token provided by the client for progress tracking, or null
     * @param exchange The MCP exchange for sending progress notifications
     * @param argumentMapper Function to map arguments to the command
     * @return The result of the tool execution
     */
    fun execute(
        entry: McpCommandEntry,
        arguments: Map<String, Any>?,
        progressToken: Any?,
        exchange: McpSyncServerExchange,
        argumentMapper: (PicoCommand, Map<String, Any>) -> Unit,
    ): McpSchema.CallToolResult {
        log.info { "Executing tool: ${entry.toolName} with arguments: $arguments, progressToken: $progressToken" }

        val progressNotifier = McpProgressNotifier(exchange, progressToken)

        // Subscribe to receive output during execution
        outputHandler.subscribe(progressNotifier)

        val future =
            executor.submit(
                Callable {
                    executeCommand(entry, arguments, argumentMapper, progressNotifier)
                },
            )

        return try {
            future.get(Constants.MCP.TOOL_TIMEOUT_MINUTES, TimeUnit.MINUTES)
        } catch (e: TimeoutException) {
            log.error { "Tool ${entry.toolName} timed out after ${Constants.MCP.TOOL_TIMEOUT_MINUTES} minutes" }
            future.cancel(true)
            buildErrorResult(entry.toolName, "Timeout after ${Constants.MCP.TOOL_TIMEOUT_MINUTES} minutes")
        } catch (e: ExecutionException) {
            log.error(e.cause) { "Tool ${entry.toolName} failed with exception" }
            buildErrorResult(entry.toolName, e.cause?.message ?: "Unknown error")
        } catch (e: InterruptedException) {
            log.error { "Tool ${entry.toolName} was interrupted" }
            Thread.currentThread().interrupt()
            buildErrorResult(entry.toolName, "Execution interrupted")
        } finally {
            // Always unsubscribe when done
            outputHandler.unsubscribe(progressNotifier)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun executeCommand(
        entry: McpCommandEntry,
        arguments: Map<String, Any>?,
        argumentMapper: (PicoCommand, Map<String, Any>) -> Unit,
        progressNotifier: McpProgressNotifier,
    ): McpSchema.CallToolResult =
        try {
            val command = entry.factory()
            arguments?.let { argumentMapper(command, it) }

            progressNotifier.publishMessage("Starting tool: ${entry.toolName}")
            command.call()
            progressNotifier.publishMessage("Completed: ${entry.toolName}")

            buildSuccessResult(entry.toolName)
        } catch (e: Exception) {
            log.error(e) { "Error executing tool ${entry.toolName}" }
            progressNotifier.publishError("Tool failed: ${e.message}", e)
            buildErrorResult(entry.toolName, e.message ?: "Unknown error")
        }

    private fun buildSuccessResult(toolName: String): McpSchema.CallToolResult =
        McpSchema.CallToolResult
            .builder()
            .addTextContent("Tool '$toolName' executed successfully")
            .isError(false)
            .build()

    private fun buildErrorResult(
        toolName: String,
        errorMessage: String,
    ): McpSchema.CallToolResult =
        McpSchema.CallToolResult
            .builder()
            .addTextContent("Tool '$toolName' failed: $errorMessage")
            .isError(true)
            .build()

    /**
     * Shutdown the executor.
     *
     * Should be called when the MCP server stops to clean up the thread.
     */
    fun shutdown() {
        log.info { "Shutting down MCP tool executor" }
        executor.shutdown()
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow()
            }
        } catch (e: InterruptedException) {
            executor.shutdownNow()
            Thread.currentThread().interrupt()
        }
    }
}
