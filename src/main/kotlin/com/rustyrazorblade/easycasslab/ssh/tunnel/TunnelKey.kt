package com.rustyrazorblade.easycasslab.ssh.tunnel

import com.rustyrazorblade.easycasslab.configuration.Host

/**
 * Cache key for SSH tunnels.
 * A tunnel is uniquely identified by the Host it goes through and the remote port it forwards to.
 */
data class TunnelKey(
    val host: Host,
    val remotePort: Int,
)
