package com.rustyrazorblade.easycasslab.commands

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.annotations.McpCommand
import com.rustyrazorblade.easycasslab.annotations.RequireDocker
import com.rustyrazorblade.easycasslab.annotations.RequireProfileSetup
import com.rustyrazorblade.easycasslab.configuration.ClusterStateManager
import com.rustyrazorblade.easycasslab.containers.Terraform
import com.rustyrazorblade.easycasslab.output.OutputHandler
import io.github.oshai.kotlinlogging.KotlinLogging
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import java.io.File

/**
 * Shut down a cluster.
 */
@McpCommand
@RequireDocker
@RequireProfileSetup
@Command(
    name = "down",
    description = ["Shut down a cluster"],
)
class Down(
    val context: Context,
) : PicoCommand,
    KoinComponent {
    @Option(
        names = ["--auto-approve", "-a", "--yes"],
        description = ["Auto approve changes"],
    )
    var autoApprove = false

    private val outputHandler: OutputHandler by inject()
    private val clusterStateManager: ClusterStateManager by inject()
    private val log = KotlinLogging.logger {}

    data class Socks5ProxyState(
        val pid: Int,
        val port: Int,
        val controlHost: String,
        val controlIP: String,
        val clusterName: String,
        val startTime: String,
        val sshConfig: String,
    )

    override fun execute() {
        outputHandler.handleMessage("Crushing dreams, terminating instances.")

        cleanupSocks5Proxy()
        updateClusterState()

        Terraform(context).down(autoApprove)
    }

    /**
     * Cleanup SOCKS5 proxy if it exists
     */
    @Suppress("TooGenericExceptionCaught")
    private fun cleanupSocks5Proxy() {
        val proxyStateFile = File(".socks5-proxy-state")
        if (!proxyStateFile.exists()) {
            return
        }

        try {
            val mapper = jacksonObjectMapper()
            val proxyState = mapper.readValue<Socks5ProxyState>(proxyStateFile)

            // Try to kill the process
            try {
                val process = ProcessBuilder("kill", proxyState.pid.toString()).start()
                process.waitFor()
                if (process.exitValue() == 0) {
                    outputHandler.handleMessage("Stopped SOCKS5 proxy (PID: ${proxyState.pid})")
                } else {
                    log.warn { "Failed to kill SOCKS5 proxy process ${proxyState.pid}, it may already be stopped" }
                }
            } catch (e: Exception) {
                log.warn(e) { "Error killing SOCKS5 proxy process ${proxyState.pid}" }
            }

            // Remove the state file
            proxyStateFile.delete()
        } catch (e: Exception) {
            log.warn(e) { "Failed to read or cleanup SOCKS5 proxy state, continuing anyway" }
            // Try to delete the file anyway
            proxyStateFile.delete()
        }
    }

    /**
     * Mark infrastructure as DOWN in cluster state
     */
    @Suppress("TooGenericExceptionCaught")
    private fun updateClusterState() {
        try {
            if (clusterStateManager.exists()) {
                val clusterState = clusterStateManager.load()
                clusterState.markInfrastructureDown()
                clusterStateManager.save(clusterState)
                outputHandler.handleMessage("Cluster state updated: infrastructure marked as DOWN")
            }
        } catch (e: Exception) {
            log.warn(e) { "Failed to update cluster state, continuing anyway" }
        }
    }
}
