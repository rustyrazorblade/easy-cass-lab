package com.rustyrazorblade.easydblab.configuration

import com.fasterxml.jackson.databind.util.ByteBufferBackedOutputStream
import com.rustyrazorblade.easydblab.core.YamlDelegate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.OutputStream
import java.nio.ByteBuffer

internal class PrometheusTest {
    val yaml by YamlDelegate()

    val config =
        prometheus {
            global {
                scrape_interval = "15s"
            }
            scrape_config {
                job_name = "prometheus"
                scrape_interval = "5s"

                static_config {
                    targets = listOf("127.0.0.1:8000", "192.168.1:8000")
                }
            }

            scrape_config {
                job_name = "cassandra"
                scrape_interval = "5s"

                static_config {
                    targets = listOf("127.0.0.1:9501")
                    relabel_config {
                        source_labels = listOf("__meta_ec2_availability_zone")
                        regex = "(.+)"
                        target_label = "rack"
                        action = "replace"
                    }
                }
            }
        }

    @Test
    fun testGlobalConfig() {
        assertThat(config.global.scrape_interval).isEqualTo("15s")
    }

    @Test
    fun testScrapeConfigs() {
        val scrape = config.scrape_configs
        assertThat(scrape).isNotEmpty()

        val prom = config.scrape_configs.first()
        assertThat(prom.job_name).isEqualTo("prometheus")
    }

    @Test
    fun testStaticConfigs() {
        val targets =
            config.scrape_configs
                .first()
                .staticConfigList
                .first()
                .targets
        assertThat(targets).containsExactly("127.0.0.1:8000", "192.168.1:8000")
    }

    @Test
    fun testConvertToYaml() {
        var data = yaml.writeValueAsString(config)
    }

    @Test
    fun testRelabel() {
        var data = yaml.writeValueAsString(config)
        println(data)
    }

    @Test
    fun testFullExecution() {
        fun stream(): OutputStream = ByteBufferBackedOutputStream(ByteBuffer.allocate(1024))
        val c = listOf(HostInfo("192.168.1.1", name = "test1"), HostInfo("192.168.1.2", name = "test2"))
        val s = listOf(HostInfo("192.168.2.1"), HostInfo("192.168.2.2"))
        val out = stream()
        val out2 = stream()
        val out3 = stream()
        val out4 = stream()

        Prometheus.writeConfiguration(c, s, "prometheus_labels.yml", out, out2, out3, out4)
    }
}
