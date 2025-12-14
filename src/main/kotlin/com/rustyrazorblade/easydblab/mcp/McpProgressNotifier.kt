package com.rustyrazorblade.easydblab.mcp

import com.github.dockerjava.api.model.Frame
import com.rustyrazorblade.easydblab.output.OutputHandler
import io.modelcontextprotocol.server.McpSyncServerExchange
import io.modelcontextprotocol.spec.McpSchema.ProgressNotification

/**
 * OutputHandler implementation that sends messages as MCP progress notifications.
 *
 * This handler forwards output messages to an MCP client through the server exchange,
 * enabling real-time progress updates during long-running tool executions.
 * Frame events (Docker container output) are intentionally filtered out to reduce noise.
 *
 * Progress notifications are only sent if the client provided a progressToken in the request.
 * This follows the MCP protocol specification where the client must opt-in to progress updates.
 *
 * @param exchange The MCP server exchange for sending progress notifications
 * @param progressToken The token provided by the client, or null if no progress tracking requested
 */
class McpProgressNotifier(
    private val exchange: McpSyncServerExchange,
    private val progressToken: Any?,
) : OutputHandler {
    private var messageCount = 0

    /**
     * Publishes a message as an MCP progress notification.
     *
     * Each message increments the progress counter, providing a simple
     * monotonically increasing progress indicator.
     *
     * @param message The status message to send to the client
     */
    override fun publishMessage(message: String) {
        messageCount++
        sendNotification(message)
    }

    /**
     * Publishes an error message as an MCP progress notification with [ERROR] prefix.
     *
     * @param message The error message
     * @param throwable Optional exception that caused the error
     */
    override fun publishError(
        message: String,
        throwable: Throwable?,
    ) {
        messageCount++
        sendNotification("[ERROR] $message")
    }

    /**
     * Intentionally does nothing for frame events.
     *
     * Docker container output frames are too verbose for MCP streaming.
     * Use FilteringChannelOutputHandler if you need periodic frame activity updates.
     *
     * @param frame The Docker container output frame (ignored)
     */
    override fun publishFrame(frame: Frame) {
        // Intentionally filtered - too noisy for MCP streaming
    }

    /**
     * No-op close implementation.
     */
    override fun close() {
        // No resources to clean up
    }

    private fun sendNotification(message: String) {
        // Only send progress notifications if client provided a token
        val token = progressToken ?: return

        val notification =
            ProgressNotification(
                token, // Use client-provided progressToken
                messageCount.toDouble(), // progress
                null, // total (unknown)
                message, // message
            )
        exchange.progressNotification(notification)
    }
}
