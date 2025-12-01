package com.rustyrazorblade.easydblab

import org.junit.jupiter.api.Test

class MainTest {
    @Test
    fun basicTest() {
        com.rustyrazorblade.easydblab.main(
            arrayOf(
                "init",
                "easy-db-lab",
                "no ticket",
                "automated test suite",
                "-s",
                "1",
            ),
        )
        com.rustyrazorblade.easydblab.main(arrayOf("up", "--yes"))
        com.rustyrazorblade.easydblab.main(arrayOf("use", "3.11.4"))
        com.rustyrazorblade.easydblab.main(arrayOf("install"))
        com.rustyrazorblade.easydblab.main(arrayOf("start"))
        com.rustyrazorblade.easydblab.main(arrayOf("down", "--yes"))
        com.rustyrazorblade.easydblab.main(arrayOf("clean"))
    }

    fun init() {
    }
}
