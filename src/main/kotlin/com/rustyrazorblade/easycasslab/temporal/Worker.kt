package com.rustyrazorblade.easycasslab.temporal

import com.rustyrazorblade.easycasslab.temporal.workflows.RollingRestartActivitiesImpl
import com.rustyrazorblade.easycasslab.temporal.workflows.RollingRestartWorkflowImpl
import io.temporal.client.WorkflowClient
import io.temporal.serviceclient.WorkflowServiceStubs
import io.temporal.serviceclient.WorkflowServiceStubsOptions
import io.temporal.worker.WorkerFactory

// TODO (jwest): make it able to connect to a real Temporal service / not localhost
class Worker : Runnable {
    override fun run() {
        // TODO (jwest): should we create this in class initialization instead?
        val options = WorkflowServiceStubsOptions.newBuilder()
            .setTarget("localhost:7233")
            .build()
        val service = WorkflowServiceStubs.newServiceStubs(options)
        val client = WorkflowClient.newInstance(service)
        val factory = WorkerFactory.newInstance(client)

        val worker = factory.newWorker(Config.TASK_QUEUE)
        worker.registerWorkflowImplementationTypes(RollingRestartWorkflowImpl::class.java)

        // if we need a DB connection, we created it first, then pass it to the Activities implementation
        // so for example create a java DB pool here or connect to a C* cluster
        worker.registerActivitiesImplementations(RollingRestartActivitiesImpl())
        println("Starting Worker")
        factory.start()
        println("Stared, running")
    }
}