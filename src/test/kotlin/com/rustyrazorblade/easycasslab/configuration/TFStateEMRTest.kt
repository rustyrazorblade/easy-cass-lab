package com.rustyrazorblade.easycasslab.configuration

import com.rustyrazorblade.easycasslab.BaseKoinTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream

class TFStateEMRTest : BaseKoinTest() {
    @Test
    fun `getEMRCluster should return cluster info when EMR cluster exists in state`() {
        val tfStateJson =
            """
            {
              "resources": [
                {
                  "mode": "managed",
                  "type": "aws_emr_cluster",
                  "name": "cluster",
                  "instances": [
                    {
                      "attributes": {
                        "id": "j-ABC123XYZ",
                        "name": "cluster",
                        "master_public_dns": "ec2-54-123-45-67.compute-1.amazonaws.com",
                        "cluster_state": "WAITING"
                      }
                    }
                  ]
                }
              ]
            }
            """.trimIndent()

        val tfState = TFState(context, ByteArrayInputStream(tfStateJson.toByteArray()))
        val clusterInfo = tfState.getEMRCluster()

        assertThat(clusterInfo).isNotNull
        assertThat(clusterInfo!!.clusterId).isEqualTo("j-ABC123XYZ")
        assertThat(clusterInfo.name).isEqualTo("cluster")
        assertThat(clusterInfo.masterPublicDns).isEqualTo("ec2-54-123-45-67.compute-1.amazonaws.com")
        assertThat(clusterInfo.state).isEqualTo("WAITING")
    }

    @Test
    fun `getEMRCluster should return null when no EMR cluster exists in state`() {
        val tfStateJson =
            """
            {
              "resources": [
                {
                  "mode": "managed",
                  "type": "aws_instance",
                  "name": "cassandra_servers",
                  "instances": []
                }
              ]
            }
            """.trimIndent()

        val tfState = TFState(context, ByteArrayInputStream(tfStateJson.toByteArray()))
        val clusterInfo = tfState.getEMRCluster()

        assertThat(clusterInfo).isNull()
    }

    @Test
    fun `getEMRCluster should return null when terraform state is empty`() {
        val tfStateJson =
            """
            {
              "resources": []
            }
            """.trimIndent()

        val tfState = TFState(context, ByteArrayInputStream(tfStateJson.toByteArray()))
        val clusterInfo = tfState.getEMRCluster()

        assertThat(clusterInfo).isNull()
    }

    @Test
    fun `getEMRCluster should handle missing optional attributes gracefully`() {
        val tfStateJson =
            """
            {
              "resources": [
                {
                  "mode": "managed",
                  "type": "aws_emr_cluster",
                  "name": "cluster",
                  "instances": [
                    {
                      "attributes": {
                        "id": "j-ABC123XYZ",
                        "name": "cluster"
                      }
                    }
                  ]
                }
              ]
            }
            """.trimIndent()

        val tfState = TFState(context, ByteArrayInputStream(tfStateJson.toByteArray()))
        val clusterInfo = tfState.getEMRCluster()

        assertThat(clusterInfo).isNotNull
        assertThat(clusterInfo!!.clusterId).isEqualTo("j-ABC123XYZ")
        assertThat(clusterInfo.name).isEqualTo("cluster")
        assertThat(clusterInfo.masterPublicDns).isNull()
        assertThat(clusterInfo.state).isNull()
    }

    @Test
    fun `getEMRCluster should handle cluster in different states`() {
        val states = listOf("STARTING", "BOOTSTRAPPING", "RUNNING", "WAITING", "TERMINATING", "TERMINATED", "TERMINATED_WITH_ERRORS")

        states.forEach { state ->
            val tfStateJson =
                """
                {
                  "resources": [
                    {
                      "mode": "managed",
                      "type": "aws_emr_cluster",
                      "name": "cluster",
                      "instances": [
                        {
                          "attributes": {
                            "id": "j-ABC123XYZ",
                            "name": "cluster",
                            "cluster_state": "$state"
                          }
                        }
                      ]
                    }
                  ]
                }
                """.trimIndent()

            val tfState = TFState(context, ByteArrayInputStream(tfStateJson.toByteArray()))
            val clusterInfo = tfState.getEMRCluster()

            assertThat(clusterInfo).isNotNull
            assertThat(clusterInfo!!.state).isEqualTo(state)
        }
    }

    @Test
    fun `getEMRCluster should return null when cluster resource has no instances`() {
        val tfStateJson =
            """
            {
              "resources": [
                {
                  "mode": "managed",
                  "type": "aws_emr_cluster",
                  "name": "cluster",
                  "instances": []
                }
              ]
            }
            """.trimIndent()

        val tfState = TFState(context, ByteArrayInputStream(tfStateJson.toByteArray()))
        val clusterInfo = tfState.getEMRCluster()

        assertThat(clusterInfo).isNull()
    }
}
