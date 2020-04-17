package com.thelastpickle.tlpcluster.commands

import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import com.thelastpickle.tlpcluster.Context
import com.thelastpickle.tlpcluster.containers.Pssh
import com.thelastpickle.tlpcluster.configuration.ServerType
import java.io.File

@Parameters(commandDescription = "Start cassandra on all nodes via service command")
class Start(val context: Context) : ICommand {

    override fun execute() {
        val sshKeyPath = context.userConfig.sshKeyPath

        check(sshKeyPath.isNotBlank())

        if (!File(sshKeyPath).exists()) {
            println("Unable to find SSH key $sshKeyPath. Aborting start.")
            return
        }

        println("Starting all nodes.")
        val parallelSsh = Pssh(context, sshKeyPath)
        val successMessage = "service successfully started"
        val failureMessage = "service failed to started"

        var serviceName = "cassandra"
        parallelSsh.startService(ServerType.Cassandra, serviceName).fold({
            println("$serviceName $successMessage")}, {
            println("$serviceName $failureMessage. ${it.message}")
            return
        })

        val monitoringHost = context.tfstate.getHosts(ServerType.Monitoring)
        val cassandraHosts = context.tfstate.getHosts(ServerType.Cassandra)

        if (monitoringHost.count() > 0) {

            serviceName = "prometheus"
            parallelSsh.startService(ServerType.Monitoring, serviceName).fold({
                println("$serviceName $successMessage")

                serviceName = "grafana-server"
                parallelSsh.startService(ServerType.Monitoring, serviceName).fold({
                    println("Grafana started")

                    println("$serviceName $successMessage")
                    println("""
You can access the monitoring UI using the following URLs:
 - Prometheus: http://${monitoringHost.first().public}:9090
 - Grafana:    http://${monitoringHost.first().public}:3000
                        """)
                }, {
                    // error starting grafana
                    println("$serviceName $failureMessage. ${it.message}")
                })
            }, {
                // error starting prometheus
                println("$serviceName $failureMessage. ${it.message}")
            })
        }
    }
}