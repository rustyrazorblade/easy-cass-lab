package com.rustyrazorblade.easydblab.configuration

import com.rustyrazorblade.easydblab.output.OutputHandler
import io.github.classgraph.ClassGraph
import org.apache.commons.io.FileUtils
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File

/**
 * simple class to manage copying dashboards to the right directory
 */
class Dashboards(
    private val dashboardLocation: File,
) : KoinComponent {
    private val outputHandler: OutputHandler by inject()

    fun copyDashboards() {
        ClassGraph()
            .acceptPackages("com.rustyrazorblade.dashboards")
            .scan()
            .use { scanResult ->
                val resources = scanResult.allResources
                for (resource in resources) {
                    resource.open().use { input ->
                        val outputFile = resource.path.replace("com/rustyrazorblade/dashboards/", "")
                        val output = File(dashboardLocation, outputFile)
                        outputHandler.handleMessage("Writing ${output.absolutePath}")
                        FileUtils.copyInputStreamToFile(input, output)
                    }
                }
            }
    }
}
