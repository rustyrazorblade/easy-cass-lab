package com.rustyrazorblade.easycasslab.commands

import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.annotations.McpCommand
import com.rustyrazorblade.easycasslab.output.OutputHandler
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File

@McpCommand
class Clean(val context: Context) : ICommand, KoinComponent {
    private val outputHandler: OutputHandler by inject()

    companion object {
        val filesToClean =
            listOf(
                "create_provisioning_resources.sh",
                "cassandra.patch.yaml",
                "jmx.options",
                "seeds.txt",
                "terraform.tfstate",
                "terraform.tfstate.backup",
                "stress_ips.txt",
                "hosts.txt",
                "terraform.tf.json",
                "terraform.tfvars",
                "sshConfig",
                "env.sh",
                "environment.sh",
                "setup_instance.sh",
                ".terraform.lock.hcl",
                "logs",
                "state.json",
                "axonops-dashboards.json",
                "cassandra_versions.yaml",
                "axonops-workbench.json",
                "easy-cass-mcp.json",
            )

        val directoriesToClean =
            listOf(
                ".terraform",
                "provisioning",
                "control",
                "cassandra",
                "5.0",
            )
    }

    override fun execute() {
        for (f in filesToClean) {
            File(f).deleteRecursively()
        }

        for (d in directoriesToClean) {
            File(d).deleteRecursively()
        }
        val artifacts = File("artifacts")

        if (artifacts.isDirectory) {
            if (artifacts.listFiles().isEmpty()) {
                artifacts.delete()
            } else {
                outputHandler.handleMessage(
                    "Not deleting artifacts directory, it contains artifacts.",
                )
            }
        }
    }
}
