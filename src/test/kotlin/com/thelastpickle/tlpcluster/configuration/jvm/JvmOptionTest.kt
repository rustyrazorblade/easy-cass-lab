package com.thelastpickle.tlpcluster.configuration.jvm

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

internal class JvmOptionTest {
    @Test
    fun testOptionParsingSimpleFlag() {
        val result = JvmOption.parse("-ea")
        assertThat(result).isInstanceOf(JvmOption.Flag::class.java)
        (result as JvmOption.Flag).run {
            assertThat(value).isEqualTo("ea")
        }
        assertThat(result.toString()).isEqualTo("-ea")
    }

    @Test
    fun testPairWithEquals() {
        val line = "-XX:StringTableSize=1000002"
        val result = JvmOption.parse(line)
        assertThat(result).isInstanceOf(JvmOption.Pair::class.java)

        (result as JvmOption.Pair).run {
            assertThat(key).isEqualTo("XX:StringTableSize")
            assertThat(value).isEqualTo("1000002")
        }
        assertThat(result.toString()).isEqualTo(line)

    }

    @Test
    fun testDynamicParsing() {
        val line = "-Dcassandra.available_processors=number_of_processors"
        val result = JvmOption.parse(line)
        assertThat(result).isInstanceOf(JvmOption.DynamicOption::class.java)
        assertThat(result.toString()).isEqualTo(line)
    }

    @Test
    fun testBooleanParsing() {
        val line = "-XX:-UseBiasedLocking"
        val result = JvmOption.parse(line)
        assertThat(result).isInstanceOf(JvmOption.BooleanOption::class.java)
        assertThat(result.toString()).isEqualTo(line)
    }

    @Test
    fun testJavaHeap() {
        val line = "-Xmx16G"
        val result = JvmOption.parse(line)
        assertThat(result).isInstanceOf(JvmOption.HeapOption::class.java)
        (JvmOption.parse("-Xms16G") as JvmOption.HeapOption).run {
            assertThat(key.ordinal == 2)
        }
    }

    @Test
    fun testHeapCompare() {
        val h1 = JvmOption.parse("-Xmx16G")
        val h2 = JvmOption.parse("-Xmx16G")
        assertThat(h1.isKeyEqual(h2)).isTrue()

        val h3 = JvmOption.parse("-Xms16G")
        assertThat(h1.isKeyEqual(h3)).isFalse()

    }

}