package com.rustyrazorblade.easycasslab

import org.junit.jupiter.api.Test

class MainTest {
    @Test
    fun basicTest() {
        com.rustyrazorblade.easycasslab.main(
            arrayOf(
                "init",
                "easy-cass-lab",
                "no ticket",
                "automated test suite",
                "-s",
                "1",
            ),
        )
        com.rustyrazorblade.easycasslab.main(arrayOf("up", "--yes"))
        com.rustyrazorblade.easycasslab.main(arrayOf("use", "3.11.4"))
        com.rustyrazorblade.easycasslab.main(arrayOf("install"))
        com.rustyrazorblade.easycasslab.main(arrayOf("start"))
        com.rustyrazorblade.easycasslab.main(arrayOf("down", "--yes"))
        com.rustyrazorblade.easycasslab.main(arrayOf("clean"))
    }

    fun init() {
    }
}
