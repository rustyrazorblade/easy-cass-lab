package com.rustyrazorblade.easycasslab.commands.delegates

import com.beust.jcommander.Parameter

// https://github.com/rustyrazorblade/easy-cass-lab/issues/232
class SparkInitParams {
    @Parameter(names = ["--spark.enable"], description = "Enable Spark EMR Cluster")
    var enable = false

    @Parameter(names = ["--spark.master.instance.type"], description = "Master Instance Type")
    var masterInstanceType: String = "m4.large"

    @Parameter(names = ["--spark.worker.instance.type"], description = "Worker Instance Type")
    var workerInstanceType: String = "c4.large"

    @Parameter(names = ["--spark.worker.instance.count"], description = "Worker Instance Count")
    var workerCount: Int = 3
}
