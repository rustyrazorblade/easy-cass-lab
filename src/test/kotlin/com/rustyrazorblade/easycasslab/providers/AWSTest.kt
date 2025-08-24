package com.rustyrazorblade.easycasslab.providers

import com.rustyrazorblade.easycasslab.providers.aws.Clients
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import software.amazon.awssdk.services.iam.IamClient
import software.amazon.awssdk.services.iam.model.AttachRolePolicyRequest
import software.amazon.awssdk.services.iam.model.AttachRolePolicyResponse
import software.amazon.awssdk.services.iam.model.CreateRoleRequest
import software.amazon.awssdk.services.iam.model.CreateRoleResponse
import software.amazon.awssdk.services.iam.model.EntityAlreadyExistsException
import software.amazon.awssdk.services.iam.model.Role

internal class AWSTest {
    @Test
    fun createEMRServiceRoleSuccess() {
        // Create mock IAM client
        val mockIamClient = mock<IamClient>()

        // Create mock Clients with the mock IAM client
        val mockClients = mock<Clients>()
        whenever(mockClients.iam).thenReturn(mockIamClient)

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

        // Create AWS instance with mocked clients
        val aws = AWS(mockClients)

        // Execute the method
        val result = aws.createServiceRole()

        // Verify the expected calls were made
        verify(mockIamClient).createRole(any<CreateRoleRequest>())
        verify(mockIamClient).attachRolePolicy(any<AttachRolePolicyRequest>())

        // Assert the result
        assert(result == AWS.SERVICE_ROLE)
    }

    @Test
    fun createEMRServiceRoleAlreadyExists() {
        // Create mock IAM client
        val mockIamClient = mock<IamClient>()

        // Create mock Clients with the mock IAM client
        val mockClients = mock<Clients>()
        whenever(mockClients.iam).thenReturn(mockIamClient)

        // Setup mock to throw EntityAlreadyExistsException
        whenever(mockIamClient.createRole(any<CreateRoleRequest>()))
            .thenThrow(
                EntityAlreadyExistsException.builder()
                    .message("Role already exists")
                    .build(),
            )

        // Create AWS instance with mocked clients
        val aws = AWS(mockClients)

        // Execute the method - should not throw exception
        val result = aws.createServiceRole()

        // Verify the createRole was attempted
        verify(mockIamClient).createRole(any<CreateRoleRequest>())

        // Assert the result
        assert(result == AWS.SERVICE_ROLE)
    }
}
