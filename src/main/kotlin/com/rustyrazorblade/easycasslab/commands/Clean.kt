package com.rustyrazorblade.easycasslab.commands

import java.io.File

class Clean : ICommand {
    override fun execute() {
        val toDelete =
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
            )

        for (f in toDelete) {
            File(f).deleteRecursively()
        }
        File(".terraform").deleteRecursively()
        File("provisioning").deleteRecursively()
        val artifacts = File("artifacts")

        if (artifacts.isDirectory) {
            if (artifacts.listFiles().isEmpty()) {
                artifacts.delete()
            } else {
                println("Not deleting artifacts directory, it contains artifacts.")
            }
        }
    }
}
