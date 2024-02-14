package com.rustyrazorblade.easycasslab.configuration

import org.apache.commons.io.FileUtils
import org.reflections.Reflections
import org.reflections.scanners.ResourcesScanner
import java.io.File


/**
 * simple class to manage copying dashboards to the right directory
 */
class Dashboards(private val dashboardLocation: File) {
    fun copyDashboards() {
        val reflections = Reflections("com.rustyrazorblade.dashboards", ResourcesScanner())
        val resources = reflections.getResources(".*".toPattern())
        for(f in resources) {
            val input = this.javaClass.getResourceAsStream("/" + f)
            val outputFile = f.replace("com/rustyrazorblade/dashboards", "")
            val output = File(dashboardLocation, outputFile)
            println("Writing ${output.absolutePath}")
            FileUtils.copyInputStreamToFile(input, output)
        }

    }
}