package com.rustyrazorblade.easydblab.commands.mixins

import picocli.CommandLine.Option

/**
 * PicoCLI mixin for Spark EMR cluster initialization parameters.
 *
 * Provides reusable options for configuring a Spark EMR cluster
 * including master/worker instance types and worker count.
 *
 * @see <a href="https://github.com/rustyrazorblade/easy-db-lab/issues/232">Issue #232</a>
 */
class SparkInitMixin {
    companion object {
        private const val DEFAULT_SPARK_WORKER_COUNT = 3
    }

    @Option(
        names = ["--spark.enable"],
        description = ["Enable Spark EMR Cluster"],
    )
    var enable: Boolean = false

    @Option(
        names = ["--spark.master.instance.type"],
        description = ["Master Instance Type"],
    )
    var masterInstanceType: String = "m5.xlarge"

    @Option(
        names = ["--spark.worker.instance.type"],
        description = ["Worker Instance Type"],
    )
    var workerInstanceType: String = "m5.xlarge"

    @Option(
        names = ["--spark.worker.instance.count"],
        description = ["Worker Instance Count"],
    )
    var workerCount: Int = DEFAULT_SPARK_WORKER_COUNT
}
