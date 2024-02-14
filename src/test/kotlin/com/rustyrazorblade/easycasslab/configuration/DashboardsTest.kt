package com.rustyrazorblade.easycasslab.configuration

import org.junit.jupiter.api.Test
import java.nio.file.Path

import org.junit.jupiter.api.io.TempDir

import org.assertj.core.api.Assertions.assertThat


internal class DashboardsTest {

    @TempDir
    lateinit var output : Path

    @Test
    fun copyDashboards() {
        val dir = output.resolve("dashboards").toFile()
        dir.mkdir()
        Dashboards(dir).copyDashboards()
        val files = dir.listFiles()
        assertThat(files.size).isGreaterThan(2)
    }
}