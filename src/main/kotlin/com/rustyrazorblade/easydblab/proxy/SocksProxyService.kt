package com.rustyrazorblade.easydblab.proxy

import com.rustyrazorblade.easydblab.configuration.ClusterHost
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

/**
 * State of an active SOCKS5 proxy connection
 *
 * @property localPort The local port the proxy listens on
 * @property gatewayHost Full host info for the SSH gateway
 * @property startTime When the proxy was started
 * @property connectionCount Tracks usage in server mode
 */
data class SocksProxyState(
    val localPort: Int,
    val gatewayHost: ClusterHost,
    val startTime: Instant,
    val connectionCount: AtomicInteger = AtomicInteger(0),
)

/**
 * Service interface for managing a SOCKS5 proxy via SSH dynamic port forwarding.
 *
 * Supports two usage modes:
 * - CLI mode: Start proxy -> use -> stop (per-command lifecycle)
 * - Server mode (MCP): Start proxy once -> reuse across multiple requests -> stop on shutdown
 *
 * The implementation is thread-safe and gateway-agnostic, meaning it can connect
 * to any SSH-accessible host (not hardcoded to a specific node type).
 */
interface SocksProxyService {
    /**
     * Starts proxy if not running, or returns existing state if already running.
     * Idempotent - safe to call multiple times.
     *
     * If a proxy is already running to a different host, it will be stopped first.
     *
     * @param gatewayHost The host to use as SSH gateway for the proxy
     * @return The proxy state
     */
    fun ensureRunning(gatewayHost: ClusterHost): SocksProxyState

    /**
     * Explicitly start a new proxy connection.
     *
     * @param gatewayHost The host to use as SSH gateway for the proxy
     * @return The proxy state
     * @throws IllegalStateException if already running to a different host
     */
    fun start(gatewayHost: ClusterHost): SocksProxyState

    /**
     * Stop the proxy and release resources.
     * Safe to call multiple times.
     */
    fun stop()

    /**
     * Check if proxy is currently running and healthy.
     *
     * @return true if the proxy is running and the underlying session is open
     */
    fun isRunning(): Boolean

    /**
     * Get current proxy state.
     *
     * @return The current state, or null if not running
     */
    fun getState(): SocksProxyState?

    /**
     * Get the local port the proxy listens on.
     *
     * @return The configured local port
     */
    fun getLocalPort(): Int
}
