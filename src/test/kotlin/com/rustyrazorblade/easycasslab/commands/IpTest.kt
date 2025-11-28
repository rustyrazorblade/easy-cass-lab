package com.rustyrazorblade.easycasslab.commands

import com.rustyrazorblade.easycasslab.BaseKoinTest
import com.rustyrazorblade.easycasslab.configuration.TFState
import com.rustyrazorblade.easycasslab.di.TFStateProvider
import com.rustyrazorblade.easycasslab.output.OutputHandler
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.test.get
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream

class IpTest : BaseKoinTest() {
    private lateinit var mockOutputHandler: OutputHandler

    private val tfStateJson =
        """
        {
          "resources": [
            {
              "mode": "managed",
              "type": "aws_instance",
              "name": "cassandra",
              "instances": [
                {
                  "attributes": {
                    "public_ip": "54.1.2.3",
                    "private_ip": "10.0.1.100",
                    "availability_zone": "us-west-2a",
                    "tags": { "Name": "cassandra0" }
                  }
                },
                {
                  "attributes": {
                    "public_ip": "54.1.2.4",
                    "private_ip": "10.0.1.101",
                    "availability_zone": "us-west-2b",
                    "tags": { "Name": "cassandra1" }
                  }
                }
              ]
            },
            {
              "mode": "managed",
              "type": "aws_instance",
              "name": "stress",
              "instances": [
                {
                  "attributes": {
                    "public_ip": "54.2.3.4",
                    "private_ip": "10.0.2.100",
                    "availability_zone": "us-west-2a",
                    "tags": { "Name": "stress0" }
                  }
                }
              ]
            }
          ]
        }
        """.trimIndent()

    override fun additionalTestModules(): List<Module> =
        listOf(
            module {
                // Mock OutputHandler to capture output
                single {
                    mock<OutputHandler>().also {
                        mockOutputHandler = it
                    }
                }

                // Real TFState with test data
                single<TFStateProvider> {
                    object : TFStateProvider {
                        override fun parseFromFile(file: File): TFState = TFState(get(), file.inputStream())

                        override fun parseFromStream(stream: InputStream): TFState = TFState(get(), stream)

                        override fun getDefault(): TFState = TFState(get(), ByteArrayInputStream(tfStateJson.toByteArray()))
                    }
                }
            },
        )

    @Test
    fun `returns public IP by default`() {
        val command = Ip(context)
        command.host = "cassandra0"

        command.execute()

        val captor = argumentCaptor<String>()
        verify(mockOutputHandler).handleMessage(captor.capture())
        assertThat(captor.firstValue).isEqualTo("54.1.2.3")
    }

    @Test
    fun `returns public IP when --public flag is set`() {
        val command = Ip(context)
        command.host = "cassandra0"
        command.publicIp = true

        command.execute()

        val captor = argumentCaptor<String>()
        verify(mockOutputHandler).handleMessage(captor.capture())
        assertThat(captor.firstValue).isEqualTo("54.1.2.3")
    }

    @Test
    fun `returns private IP when --private flag is set`() {
        val command = Ip(context)
        command.host = "cassandra0"
        command.privateIp = true

        command.execute()

        val captor = argumentCaptor<String>()
        verify(mockOutputHandler).handleMessage(captor.capture())
        assertThat(captor.firstValue).isEqualTo("10.0.1.100")
    }

    @Test
    fun `finds host across different server types`() {
        val command = Ip(context)
        command.host = "stress0"
        command.privateIp = true

        command.execute()

        val captor = argumentCaptor<String>()
        verify(mockOutputHandler).handleMessage(captor.capture())
        assertThat(captor.firstValue).isEqualTo("10.0.2.100")
    }

    @Test
    fun `returns correct IP for second cassandra node`() {
        val command = Ip(context)
        command.host = "cassandra1"
        command.privateIp = true

        command.execute()

        val captor = argumentCaptor<String>()
        verify(mockOutputHandler).handleMessage(captor.capture())
        assertThat(captor.firstValue).isEqualTo("10.0.1.101")
    }

    @Test
    fun `throws error when host not found`() {
        val command = Ip(context)
        command.host = "nonexistent"

        assertThatThrownBy { command.execute() }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("Host not found: nonexistent")
    }

    @Test
    fun `throws error when no host alias provided`() {
        val command = Ip(context)
        command.host = ""

        assertThatThrownBy { command.execute() }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("Host not found:")
    }
}
