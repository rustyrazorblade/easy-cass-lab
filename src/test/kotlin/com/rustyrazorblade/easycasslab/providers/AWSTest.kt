package com.rustyrazorblade.easycasslab.providers

import com.rustyrazorblade.easycasslab.BaseKoinTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import software.amazon.awssdk.services.iam.model.AttachRolePolicyRequest
import software.amazon.awssdk.services.iam.model.AttachRolePolicyResponse
import software.amazon.awssdk.services.iam.model.CreateRoleRequest
import software.amazon.awssdk.services.iam.model.CreateRoleResponse
import software.amazon.awssdk.services.iam.model.EntityAlreadyExistsException
import software.amazon.awssdk.services.iam.model.Role

internal class AWSTest : BaseKoinTest(), KoinComponent {
    // Inject the mocked AWS service from BaseKoinTest
    private val aws: AWS by inject()

    @Test
    fun createEMRServiceRoleSuccess() {
        // Get the mocked IAM client from the injected AWS service
        val mockIamClient = aws.clients.iam

        // Setup mock responses
        val mockRole =
            Role.builder()
                .roleName(AWS.SERVICE_ROLE)
                .arn("arn:aws:iam::123456789:role/${AWS.SERVICE_ROLE}")
                .build()

        val createRoleResponse =
            CreateRoleResponse.builder()
                .role(mockRole)
                .build()

        whenever(mockIamClient.createRole(any<CreateRoleRequest>())).thenReturn(createRoleResponse)

        val attachPolicyResponse = AttachRolePolicyResponse.builder().build()
        whenever(mockIamClient.attachRolePolicy(any<AttachRolePolicyRequest>())).thenReturn(attachPolicyResponse)

        // Execute the method
        val result = aws.createServiceRole()

        // Verify the expected calls were made
        verify(mockIamClient).createRole(any<CreateRoleRequest>())
        verify(mockIamClient).attachRolePolicy(any<AttachRolePolicyRequest>())

        // Assert the result using AssertJ
        assertThat(result).isEqualTo(AWS.SERVICE_ROLE)
    }

    @Test
    fun createEMRServiceRoleAlreadyExists() {
        // Get the mocked IAM client from the injected AWS service
        val mockIamClient = aws.clients.iam

        // Setup mock to throw EntityAlreadyExistsException
        whenever(mockIamClient.createRole(any<CreateRoleRequest>()))
            .thenThrow(
                EntityAlreadyExistsException.builder()
                    .message("Role already exists")
                    .build(),
            )

        // Execute the method - should not throw exception
        val result = aws.createServiceRole()

        // Verify the createRole was attempted
        verify(mockIamClient).createRole(any<CreateRoleRequest>())

        // Assert the result using AssertJ
        assertThat(result).isEqualTo(AWS.SERVICE_ROLE)
    }
}
