package com.rustyrazorblade.easycasslab.configuration

import com.rustyrazorblade.easycasslab.BaseKoinTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class UserTest : BaseKoinTest() {
    /**
     * Helper extension function to safely extract ConfigField annotation from a property.
     *
     * @throws AssertionError if property doesn't have @ConfigField annotation
     * @return The ConfigField annotation
     */
    private fun kotlin.reflect.KProperty1<User, *>.getConfigField(): ConfigField =
        this.annotations.find { it is ConfigField } as? ConfigField
            ?: throw AssertionError("Property ${this.name} should have @ConfigField annotation")

    @Test
    fun `getConfigFields returns all ConfigField-annotated properties`() {
        val fields = User.getConfigFields()

        // Verify we have all expected fields
        val fieldNames = fields.map { it.name }

        assertThat(fieldNames)
            .containsExactlyInAnyOrder(
                "email",
                "region",
                "keyName",
                "sshKeyPath",
                "awsProfile",
                "awsAccessKey",
                "awsSecret",
                "axonOpsOrg",
                "axonOpsKey",
//                "s3Bucket",
            )
    }

    @Test
    fun `getConfigFields returns fields sorted by ConfigField order annotation`() {
        val fields = User.getConfigFields()

        // Extract the order values from the ConfigField annotations
        val fieldOrders =
            fields.map { property ->
                property.name to property.getConfigField().order
            }

        // Verify fields are sorted by order
        val expectedOrder =
            listOf(
                "email" to 1,
                "region" to 2,
                "awsAccessKey" to 3,
                "awsSecret" to 4,
                "axonOpsOrg" to 5,
                "axonOpsKey" to 6,
                "keyName" to 10,
                "sshKeyPath" to 11,
                "awsProfile" to 12,
//                "s3Bucket" to 15,
            )

        assertThat(fieldOrders).isEqualTo(expectedOrder)
    }

    @Test
    fun `getConfigFields excludes properties without ConfigField annotation`() {
        val fields = User.getConfigFields()

        // Verify that all returned fields have the ConfigField annotation
        fields.forEach { property ->
            val hasConfigField = property.annotations.any { it is ConfigField }
            assertThat(hasConfigField)
                .withFailMessage("Property ${property.name} should have @ConfigField annotation")
                .isTrue()
        }
    }

    @Test
    fun `getConfigFields includes skippable fields`() {
        val fields = User.getConfigFields()

        // Verify that skippable fields are included
        val skippableFields =
            fields.filter { property ->
                property.getConfigField().skippable
            }

        val skippableFieldNames = skippableFields.map { it.name }

        assertThat(skippableFieldNames)
            .containsExactlyInAnyOrder(
                "keyName",
                "sshKeyPath",
                "awsProfile",
            )
    }

    @Test
    fun `getConfigFields identifies secret fields correctly`() {
        val fields = User.getConfigFields()

        // Find secret fields
        val secretFields =
            fields.filter { property ->
                property.getConfigField().secret
            }

        val secretFieldNames = secretFields.map { it.name }

        // Only awsSecret should be marked as secret
        assertThat(secretFieldNames).containsExactly("awsSecret")
    }

    @Test
    fun `getConfigFields verifies field metadata consistency`() {
        val fields = User.getConfigFields()

        // Verify each field has expected metadata
        fields.forEach { property ->
            val configField = property.getConfigField()

            // All fields should have non-empty prompts
            assertThat(configField.prompt)
                .withFailMessage("Field ${property.name} should have a non-empty prompt")
                .isNotBlank()

            // All fields should have an order >= 1
            assertThat(configField.order)
                .withFailMessage("Field ${property.name} should have order >= 1")
                .isGreaterThanOrEqualTo(1)
        }
    }
}
