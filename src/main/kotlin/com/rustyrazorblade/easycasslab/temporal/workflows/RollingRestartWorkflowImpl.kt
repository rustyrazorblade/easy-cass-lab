package com.rustyrazorblade.easycasslab.temporal.workflows

import io.temporal.activity.ActivityOptions
import io.temporal.workflow.Workflow
import java.time.Duration

class RollingRestartWorkflowImpl : RollingRestartWorkflow {
    val activityOptions = ActivityOptions.newBuilder().setScheduleToCloseTimeout(Duration.ofSeconds(240)).build()

    val activities = Workflow.newActivityStub(RollingRestartActivities::class.java, activityOptions)

    override fun perform(params: RollingRestartParams): List<Boolean> {
        return params.getIps().map { ip -> activities.restartNode(params.getSSHKeyPath(), ip) }
    }
}