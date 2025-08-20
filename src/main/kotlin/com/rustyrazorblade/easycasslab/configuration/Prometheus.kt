@file:Suppress("ConstructorParameterNaming", "FunctionNaming")

package com.rustyrazorblade.easycasslab.configuration

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.rustyrazorblade.easycasslab.core.YamlDelegate
import java.io.File
import java.io.OutputStream

/**
 * This is the top level object that holds all prometheus server configuration
 * Global is a normal scrape config
 * Generally speaking you only need to worry about making changes to the config in
 * Prometheus.writeConfiguration
 *
 * The rest is simple classes that will get taken for you automatically
 */
class Prometheus(
    var global: ScrapeConfig = ScrapeConfig(scrape_interval = "15s", fileSdConfigs = listOf()),
    var scrape_configs: MutableList<ScrapeConfig> = mutableListOf(),
) {
    /**
     * You will never need to call this directly
     * @see <a href="https://prometheus.io/docs/prometheus/latest/configuration/configuration/#scrape_config">
     *     Prometheus Scrape Config</a>
     */
    class ScrapeConfig(
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        var job_name: String = "",
        @JsonInclude(JsonInclude.Include.NON_NULL)
        var scrape_interval: String? = null,
        @JsonProperty("static_configs")
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        var staticConfigList: MutableList<StaticConfig> = mutableListOf(),
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        @JsonProperty("relabel_configs")
        var relabelConfigList: MutableList<RelabelConfig> = mutableListOf(),
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        @JsonProperty("file_sd_configs")
        var fileSdConfigs: List<Map<String, MutableList<String>>> = listOf(mapOf("files" to mutableListOf())),
    ) {
        fun static_config(block: StaticConfig.() -> Unit) {
            staticConfigList.add(StaticConfig().apply(block))
        }

        fun relabel_config(block: RelabelConfig.() -> Unit) {
            relabelConfigList.add(RelabelConfig().apply(block))
        }
    }

    class RelabelConfig(
        var source_labels: List<String> = listOf(),
        var regex: String = "",
        var action: String = "keep",
        var target_label: String = "",
    )

    /**
     * belongs to scrape config.  We're only using the targets list for now, so I haven't included anything else here
     * The only other thing we could use is labels, and that doesn't appear to be applicable here
     *
     * @see <a href="https://prometheus.io/docs/prometheus/latest/configuration/configuration/#static_config">
     *     Static Config</a>
     *
     */
    class StaticConfig(var targets: List<String> = listOf())

    /**
     * Used as Prometheus.global { }
     *
     * @param block Lambda that will be applied to the new ScapeConfig.
     */
    fun global(block: ScrapeConfig.() -> Unit) {
        global = ScrapeConfig().apply(block)
    }

    /**
     * Used as Prometheus.global {}
     *
     * @param block Lambda that will be applied to the new ScapeConfig.
     *
     * @see com.rustyrazorblade.easycasslab.configuration.PrometheusTest for usage
     */
    fun scrape_config(block: ScrapeConfig.() -> Unit) {
        val config = ScrapeConfig().apply(block)
        scrape_configs.add(config)
    }

    companion object {
        val yaml by YamlDelegate()

        fun writeConfiguration(
            cassandra: List<HostInfo>,
            stress: List<HostInfo>,
            fileSdConfigBaseDir: String,
            prometheusOut: OutputStream,
            cassandraLabelOut: OutputStream,
            cassandraOSLabelOut: OutputStream,
            stressLabelOut: OutputStream,
        ) {
            val config =
                prometheus {
                    scrape_config {
                        job_name = "prometheus"

                        static_config {
                            targets = listOf("localhost:9090")
                        }
                        this.fileSdConfigs = listOf()
                    }

                    scrape_config {
                        job_name = "cassandra"

                        fileSdConfigs[0]["files"]?.add(fileSdConfigBaseDir + "cassandra.yml")
                    }
                    scrape_config {
                        job_name = "cassandra-os"
                        scrape_interval = "5s"
                        fileSdConfigs[0]["files"]?.add(fileSdConfigBaseDir + "cassandra-os.yml")
                    }
                    scrape_config {
                        job_name = "stress"
                        scrape_interval = "5s"

                        fileSdConfigs[0]["files"]?.add(fileSdConfigBaseDir + "stress.yml")
                    }
                }

            yaml.writeValue(prometheusOut, config)

            val cassandraLabels =
                cassandra.map {
                    HostLabel("${it.address}:9501", it)
                }

            val cassandraOSLabels =
                cassandra.map {
                    HostLabel("${it.address}:9100", it)
                }

            val stressLabels =
                stress.map {
                    HostLabel("${it.address}:9500", it)
                }

            yaml.writeValue(cassandraLabelOut, cassandraLabels)
            yaml.writeValue(cassandraOSLabelOut, cassandraOSLabels)
            yaml.writeValue(stressLabelOut, stressLabels)
        }

        data class HostLabel(val targets: List<String>, val labels: HostInfo) {
            constructor(target: String, host: HostInfo) : this(listOf(target), host)
        }
    }
}

fun prometheus(block: Prometheus.() -> Unit): Prometheus {
    return Prometheus().apply(block)
}

/**
 * Convenience for generating sample configs
 * This is for local testing and debugging
 *
 */
fun main() {
    val c = listOf(HostInfo("192.168.1.1", name = "test1"), HostInfo("192.168.1.2", name = "test2"))
    val s = listOf(HostInfo("192.168.2.1"), HostInfo("192.168.2.2"))
    val out = File("prometheus.yml").outputStream()
    val out2 = File("cassandra.yml").outputStream()
    val out3 = File("cassandra-os.yml").outputStream()
    val out4 = File("stress.yml").outputStream()

    Prometheus.writeConfiguration(c, s, "prometheus_labels.yml", out, out2, out3, out4)
//    Prometheus.writeLabelFile(c, s, out2)
}
