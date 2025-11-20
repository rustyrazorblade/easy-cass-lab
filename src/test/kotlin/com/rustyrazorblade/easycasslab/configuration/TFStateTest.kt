package com.rustyrazorblade.easycasslab.configuration

import com.rustyrazorblade.easycasslab.BaseKoinTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream

class TFStateTest : BaseKoinTest() {
    @Test
    fun `getAllHostsAsMap should return all hosts organized by server type`() {
        // Create a minimal terraform state JSON with hosts
        val tfStateJson =
            """
            {
              "resources": [
                {
                  "mode": "managed",
                  "name": "cassandra_servers",
                  "instances": [
                    {
                      "attributes": {
                        "private_ip": "10.0.1.10",
                        "public_ip": "54.1.2.3",
                        "availability_zone": "us-west-2a",
                        "tags": {
                          "Name": "cassandra0"
                        }
                      }
                    },
                    {
                      "attributes": {
                        "private_ip": "10.0.1.11",
                        "public_ip": "54.1.2.4",
                        "availability_zone": "us-west-2b",
                        "tags": {
                          "Name": "cassandra1"
                        }
                      }
                    }
                  ]
                },
                {
                  "mode": "managed",
                  "name": "stress_servers",
                  "instances": [
                    {
                      "attributes": {
                        "private_ip": "10.0.1.30",
                        "public_ip": "54.1.2.6",
                        "availability_zone": "us-west-2a",
                        "tags": {
                          "Name": "stress0"
                        }
                      }
                    }
                  ]
                },
                {
                  "mode": "managed",
                  "name": "control_servers",
                  "instances": [
                    {
                      "attributes": {
                        "private_ip": "10.0.1.20",
                        "public_ip": "54.1.2.5",
                        "availability_zone": "us-west-2a",
                        "tags": {
                          "Name": "control0"
                        }
                      }
                    }
                  ]
                }
              ]
            }
            """.trimIndent()

        val tfState = TFState(context, ByteArrayInputStream(tfStateJson.toByteArray()))
        val hostsMap = tfState.getAllHostsAsMap()

        // Should have entries for each server type
        assertThat(hostsMap).containsKeys(ServerType.Cassandra, ServerType.Stress, ServerType.Control)

        // Verify Cassandra hosts
        val cassandraHosts = hostsMap[ServerType.Cassandra]!!
        assertThat(cassandraHosts).hasSize(2)
        assertThat(cassandraHosts[0].publicIp).isEqualTo("54.1.2.3")
        assertThat(cassandraHosts[0].privateIp).isEqualTo("10.0.1.10")
        assertThat(cassandraHosts[0].alias).isEqualTo("cassandra0")
        assertThat(cassandraHosts[0].availabilityZone).isEqualTo("us-west-2a")

        assertThat(cassandraHosts[1].publicIp).isEqualTo("54.1.2.4")
        assertThat(cassandraHosts[1].privateIp).isEqualTo("10.0.1.11")
        assertThat(cassandraHosts[1].alias).isEqualTo("cassandra1")
        assertThat(cassandraHosts[1].availabilityZone).isEqualTo("us-west-2b")

        // Verify Stress hosts
        val stressHosts = hostsMap[ServerType.Stress]!!
        assertThat(stressHosts).hasSize(1)
        assertThat(stressHosts[0].publicIp).isEqualTo("54.1.2.6")
        assertThat(stressHosts[0].privateIp).isEqualTo("10.0.1.30")
        assertThat(stressHosts[0].alias).isEqualTo("stress0")

        // Verify Control hosts
        val controlHosts = hostsMap[ServerType.Control]!!
        assertThat(controlHosts).hasSize(1)
        assertThat(controlHosts[0].publicIp).isEqualTo("54.1.2.5")
        assertThat(controlHosts[0].privateIp).isEqualTo("10.0.1.20")
        assertThat(controlHosts[0].alias).isEqualTo("control0")
    }

    @Test
    fun `getAllHostsAsMap should return empty lists for server types with no instances`() {
        // Create terraform state with only cassandra hosts
        val tfStateJson =
            """
            {
              "resources": [
                {
                  "mode": "managed",
                  "name": "cassandra_servers",
                  "instances": [
                    {
                      "attributes": {
                        "private_ip": "10.0.1.10",
                        "public_ip": "54.1.2.3",
                        "availability_zone": "us-west-2a",
                        "tags": {
                          "Name": "cassandra0"
                        }
                      }
                    }
                  ]
                }
              ]
            }
            """.trimIndent()

        val tfState = TFState(context, ByteArrayInputStream(tfStateJson.toByteArray()))
        val hostsMap = tfState.getAllHostsAsMap()

        // Should have all server types but some empty
        assertThat(hostsMap).containsKeys(ServerType.Cassandra, ServerType.Stress, ServerType.Control)

        assertThat(hostsMap[ServerType.Cassandra]).hasSize(1)
        assertThat(hostsMap[ServerType.Stress]).isEmpty()
        assertThat(hostsMap[ServerType.Control]).isEmpty()
    }

    @Test
    fun `getAllHostsAsMap should handle empty terraform state`() {
        val tfStateJson =
            """
            {
              "resources": []
            }
            """.trimIndent()

        val tfState = TFState(context, ByteArrayInputStream(tfStateJson.toByteArray()))
        val hostsMap = tfState.getAllHostsAsMap()

        // Should return empty lists for all server types
        assertThat(hostsMap).containsKeys(ServerType.Cassandra, ServerType.Stress, ServerType.Control)
        assertThat(hostsMap[ServerType.Cassandra]).isEmpty()
        assertThat(hostsMap[ServerType.Stress]).isEmpty()
        assertThat(hostsMap[ServerType.Control]).isEmpty()
    }
}
