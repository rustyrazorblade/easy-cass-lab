package com.rustyrazorblade.easydblab.output

import com.github.dockerjava.api.model.Frame
import java.util.concurrent.CopyOnWriteArrayList

/**
 * OutputHandler that supports dynamic subscription for additional handlers.
 *
 * Messages are always sent to the default handler, plus any currently subscribed handlers.
 * Subscribers can be added and removed at any time, enabling use cases like MCP progress
 * notifications where a handler needs to receive output only during command execution.
 *
 * Thread safety: Uses CopyOnWriteArrayList for subscribers, allowing safe concurrent
 * iteration and modification.
 *
 * Example usage:
 * ```kotlin
 * val handler = SubscribableOutputHandler(defaultHandler)
 *
 * // Subscribe to receive output
 * val subscriber = MyOutputHandler()
 * handler.subscribe(subscriber)
 *
 * // ... execute command, subscriber receives all output ...
 *
 * // Unsubscribe when done
 * handler.unsubscribe(subscriber)
 * ```
 *
 * @param defaultHandler The handler that always receives output
 */
class SubscribableOutputHandler(
    private val defaultHandler: OutputHandler,
) : OutputHandler {
    private val subscribers = CopyOnWriteArrayList<OutputHandler>()

    /**
     * Subscribe a handler to receive output.
     *
     * The handler will receive all subsequent publishMessage, publishError, and publishFrame calls
     * until unsubscribed.
     *
     * @param handler The handler to subscribe
     */
    fun subscribe(handler: OutputHandler) {
        subscribers.add(handler)
    }

    /**
     * Unsubscribe a handler from receiving output.
     *
     * @param handler The handler to unsubscribe
     */
    fun unsubscribe(handler: OutputHandler) {
        subscribers.remove(handler)
    }

    override fun publishFrame(frame: Frame) {
        defaultHandler.publishFrame(frame)
        subscribers.forEach { it.publishFrame(frame) }
    }

    override fun publishMessage(message: String) {
        defaultHandler.publishMessage(message)
        subscribers.forEach { it.publishMessage(message) }
    }

    override fun publishError(
        message: String,
        throwable: Throwable?,
    ) {
        defaultHandler.publishError(message, throwable)
        subscribers.forEach { it.publishError(message, throwable) }
    }

    override fun close() {
        defaultHandler.close()
        subscribers.forEach { it.close() }
    }
}
