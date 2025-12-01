package com.rustyrazorblade.easydblab.configuration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class SeedsTest {
    @Test
    fun ensureSeedsCanBeRead() {
        val seedFile = this.javaClass.getResourceAsStream("seeds.txt")
        val seeds = Seeds.open(seedFile)
        assertThat(seeds.seeds).hasSize(3)
    }
}
