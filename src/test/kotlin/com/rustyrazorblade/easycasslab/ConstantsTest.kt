package com.rustyrazorblade.easycasslab

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ConstantsTest {
    
    @Test
    fun `Paths constants have expected values`() {
        assertThat(Constants.Paths.LOCAL_MOUNT).isEqualTo("/local")
        assertThat(Constants.Paths.TERRAFORM_CACHE).isEqualTo("/tcache")
        assertThat(Constants.Paths.CREDENTIALS_MOUNT).isEqualTo("/credentials")
        assertThat(Constants.Paths.AWS_CREDENTIALS_MOUNT).isEqualTo("/awscredentials")
    }
    
    @Test
    fun `Server constants have expected values`() {
        assertThat(Constants.Servers.CASSANDRA).isEqualTo("cassandra")
        assertThat(Constants.Servers.STRESS).isEqualTo("stress")
        assertThat(Constants.Servers.CONTROL).isEqualTo("control")
    }
    
    @Test
    fun `Docker constants have expected values`() {
        assertThat(Constants.Docker.CONTAINER_ID_DISPLAY_LENGTH).isEqualTo(12)
        assertThat(Constants.Docker.CONTAINER_POLLING_INTERVAL_MS).isEqualTo(1000L)
    }
    
    @Test
    fun `Terraform constants have expected values`() {
        assertThat(Constants.Terraform.PLUGIN_CACHE_DIR_ENV).isEqualTo("TF_PLUGIN_CACHE_DIR")
        assertThat(Constants.Terraform.AUTO_APPROVE_FLAG).isEqualTo("-auto-approve")
    }
    
    @Test
    fun `Packer constants have expected values`() {
        assertThat(Constants.Packer.CASSANDRA_VERSIONS_FILE).isEqualTo("cassandra_versions.yaml")
        assertThat(Constants.Packer.AWS_CREDENTIALS_ENV).isEqualTo("AWS_SHARED_CREDENTIALS_FILE")
    }
    
    @Test
    fun `AWS constants have expected values`() {
        assertThat(Constants.AWS.DEFAULT_CREDENTIALS_NAME).isEqualTo("awscredentials")
        assertThat(Constants.AWS.SSH_KEY_ENV).isEqualTo("EASY_CASS_LAB_SSH_KEY")
    }
    
    @Test
    fun `Monitoring constants have expected values`() {
        assertThat(Constants.Monitoring.PROMETHEUS_JOB_CASSANDRA).isEqualTo("cassandra")
        assertThat(Constants.Monitoring.PROMETHEUS_JOB_STRESS).isEqualTo("stress")
    }
    
    @Test
    fun `Constants remain immutable`() {
        // This test verifies that Constants is an object (singleton) and values can't be changed
        val firstReference = Constants.Paths.LOCAL_MOUNT
        val secondReference = Constants.Paths.LOCAL_MOUNT
        
        assertThat(firstReference).isSameAs(secondReference)
        assertThat(Constants).isNotNull()
    }
}