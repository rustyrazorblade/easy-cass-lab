package com.rustyrazorblade.easycasslab.ssh

import org.apache.logging.log4j.kotlin.logger
import org.apache.sshd.client.SshClient
import org.apache.sshd.client.session.ClientSession

/**
 * Executes commands on a remote host
 */
class RemoteCommandExecutor(val session: ClientSession) {
    private val log = logger()
    
    /**
     * Execute a command on a remote host
     * 
     * @param host The target host
     * @param command The command to execute
     * @param output Whether to print the command output
     * @param secret Whether the command contains sensitive information
     * @return The command output
     */
    fun execute(command: String, output: Boolean = true, secret: Boolean = false): String {
        // Create connection for this host
        if (!secret) {
            println("Executing remote command: $command")
        } else {
            println("Executing remote command: [hidden]")
        }
        
        val result = session.executeRemoteCommand(command)
        
        if (output) {
            println(result)
        }
        
        return result
    }
}