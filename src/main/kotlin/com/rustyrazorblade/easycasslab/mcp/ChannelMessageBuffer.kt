package com.rustyrazorblade.easycasslab.mcp

import com.rustyrazorblade.easycasslab.output.OutputEvent
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.channels.Channel
import java.time.LocalTime
import java.util.Collections
import kotlin.concurrent.thread

/**
 * Manages buffering of messages from output events.
 *
 * This class processes OutputEvent messages from a channel and stores them in a thread-safe buffer
 * with timestamps. It manages its own consumer thread internally.
 */
class ChannelMessageBuffer(private val outputChannel: Channel<OutputEvent>) {
    companion object {
        private val log = KotlinLogging.logger {}
    }

    /**
     * Thread-safe buffer for storing timestamped messages.
     */
    private val messageBuffer = Collections.synchronizedList(mutableListOf<String>())

    /**
     * The consumer thread that reads from the channel.
     */
    private var consumerThread: Thread? = null

    /**
     * Flag indicating whether the consumer is running.
     */
    @Volatile
    private var running = false

    /**
     * Starts the consumer thread that reads from the channel and buffers messages.
     */
    fun start() {
        if (running) {
            log.debug { "Message buffer consumer already running" }
            return
        }

        running = true
        consumerThread =
            thread(start = true, name = "MCP-MessageBuffer-Consumer", isDaemon = true) {
                log.info { "Starting message buffer consumer thread" }
                try {
                    while (running) {
                        val result = outputChannel.tryReceive()
                        if (result.isSuccess) {
                            result.getOrNull()?.let { processEvent(it) }
                        } else if (result.isClosed) {
                            // do nothing
                        }
                        Thread.sleep(10) // Small delay to avoid busy-waiting
                    }
                } catch (e: Exception) {
                    log.error(e) { "Message buffer consumer thread error" }
                }
                log.info { "Message buffer consumer thread completed" }
                running = false
            }
    }

    /**
     * Stops the consumer thread.
     */
    fun stop() {
        running = false
        consumerThread?.join(1000) // Wait up to 1 second for thread to finish
    }

    /**
     * Processes an output event and adds it to the buffer if appropriate.
     *
     * @param event The OutputEvent to process
     * @return true if processing should continue, false if a CloseEvent was received
     */
    private fun processEvent(event: OutputEvent): Boolean {
        try {
            when (event) {
                is OutputEvent.MessageEvent -> {
                    val timestampedMessage = "[${LocalTime.now()}] ${event.message}"
                    messageBuffer.add(timestampedMessage)
                    log.debug { "Buffered message: ${event.message}" }
                    return true
                }
                is OutputEvent.ErrorEvent -> {
                    val timestampedMessage = "[${LocalTime.now()}] ERROR: ${event.message}"
                    messageBuffer.add(timestampedMessage)
                    log.debug { "Buffered error: ${event.message}" }
                    return true
                }
                is OutputEvent.CloseEvent -> {
                    log.debug { "Close event received" }
                    return false
                }
                is OutputEvent.FrameEvent -> {
                    // Frame events are filtered by FilteringChannelOutputHandler
                    // We log them but don't buffer them
                    log.trace { "Frame event received but not buffered" }
                    return true
                }
            }
        } catch (e: Exception) {
            log.error(e) { "Error processing output event for message buffer" }
            return true
        }
    }

    /**
     * Returns a copy of all messages currently in the buffer.
     *
     * @return List of timestamped messages
     */
    fun getMessages(): List<String> {
        return messageBuffer.toList()
    }

    /**
     * Clears all messages from the buffer.
     */
    fun clearMessages() {
        messageBuffer.clear()
    }

    /**
     * Atomically retrieves all messages and clears the buffer.
     *
     * This method is useful for consuming all messages at once while
     * ensuring no messages are lost between retrieval and clearing.
     *
     * @return List of timestamped messages that were in the buffer
     */
    fun getAndClearMessages(): List<String> {
        synchronized(messageBuffer) {
            val messages = messageBuffer.toList()
            messageBuffer.clear()
            return messages
        }
    }

    /**
     * Returns the current number of messages in the buffer.
     *
     * @return The number of messages currently buffered
     */
    fun size(): Int {
        return messageBuffer.size
    }

    /**
     * Checks if the buffer is empty.
     *
     * @return true if the buffer contains no messages, false otherwise
     */
    fun isEmpty(): Boolean {
        return messageBuffer.isEmpty()
    }
}
