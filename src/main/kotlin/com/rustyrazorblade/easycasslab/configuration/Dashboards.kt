package com.rustyrazorblade.easycasslab.configuration

import com.rustyrazorblade.easycasslab.output.OutputHandler
import org.apache.commons.io.FileUtils
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.reflections.Reflections
import org.reflections.scanners.ResourcesScanner
import java.io.File

/**
 * simple class to manage copying dashboards to the right directory
 */
class Dashboards(
    private val dashboardLocation: File,
) : KoinComponent {
    private val outputHandler: OutputHandler by inject()

    fun copyDashboards() {
        val reflections = Reflections("com.rustyrazorblade.dashboards", ResourcesScanner())
        val resources = reflections.getResources(".*".toPattern())
        for (f in resources) {
            val input = this.javaClass.getResourceAsStream("/" + f)
            val outputFile = f.replace("com/rustyrazorblade/dashboards", "")
            val output = File(dashboardLocation, outputFile)
            outputHandler.handleMessage("Writing ${output.absolutePath}")
            FileUtils.copyInputStreamToFile(input, output)
        }
    }
}
