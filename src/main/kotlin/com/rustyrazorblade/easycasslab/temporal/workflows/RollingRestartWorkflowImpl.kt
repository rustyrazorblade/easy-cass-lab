package com.rustyrazorblade.easycasslab.temporal.workflows

import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.configuration.ServerType
import io.temporal.activity.ActivityOptions
import io.temporal.workflow.Workflow
import java.io.File
import java.time.Duration

class RollingRestartWorkflowImpl : RollingRestartWorkflow {
    val activityOptions = ActivityOptions.newBuilder().setScheduleToCloseTimeout(Duration.ofSeconds(240)).build()

    val activities = Workflow.newActivityStub(RollingRestartActivities::class.java, activityOptions)

    override fun perform(params: RollingRestartParams): List<Boolean> {
        // TODO (jwest): dont hardcode location
        // TODO (jwest): get context from some kind of mixin, more workloads will need it
        val context = Context(File(System.getProperty("user.home"), "/.easy-cass-lab/"))
        return context.tfstate.getHosts(ServerType.Cassandra).map {
            host -> activities.restartNode(context.userConfig.sshKeyPath, host.public)
        }
    }
}