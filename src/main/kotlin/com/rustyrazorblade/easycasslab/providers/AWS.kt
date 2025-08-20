package com.rustyrazorblade.easycasslab.providers

import com.rustyrazorblade.easycasslab.providers.aws.Clients
import software.amazon.awssdk.services.iam.model.AddRoleToInstanceProfileRequest
import software.amazon.awssdk.services.iam.model.AttachRolePolicyRequest
import software.amazon.awssdk.services.iam.model.CreateInstanceProfileRequest
import software.amazon.awssdk.services.iam.model.CreateRoleRequest
import software.amazon.awssdk.services.iam.model.EntityAlreadyExistsException

class AWS(val clients: Clients) {
    companion object {
        const val SERVICE_ROLE = "EasyCassLabServiceRole"
    }

    fun createLabEnvironment() {
        createAccounts()
    }

    private fun createAccounts() {
        createServiceRole()
        attachEMRRole()
        attachEMREC2Role()
        createInstanceProfile()
    }

    /**
     * Creates an IAM role for EMR service with necessary permissions
     */
    fun createServiceRole(): String {
        val assumeRolePolicy = """{
            "Version": "2012-10-17",
            "Statement": [
                {
                    "Effect": "Allow",
                    "Principal": {
                        "Service": "elasticmapreduce.amazonaws.com"
                    },
                    "Action": "sts:AssumeRole"
                }
            ]
        }"""

        try {
            // Create the IAM role
            val createRoleRequest =
                CreateRoleRequest.builder()
                    .roleName(SERVICE_ROLE)
                    .assumeRolePolicyDocument(assumeRolePolicy)
                    .description("IAM role for EMR service")
                    .build()

            clients.iam.createRole(createRoleRequest)

            attachEMRRole()
        } catch (ignored: EntityAlreadyExistsException) {
            // Role already exists, continue
        }

        return SERVICE_ROLE
    }

    private fun attachPolicy(policy: String) {
        val attachPolicyRequest =
            AttachRolePolicyRequest.builder()
                .roleName(SERVICE_ROLE)
                .policyArn(policy)
                .build()
        clients.iam.attachRolePolicy(attachPolicyRequest)
    }

    private fun attachEMRRole() {
        // Attach necessary managed policy
        return attachPolicy("arn:aws:iam::aws:policy/service-role/AmazonElasticMapReduceRole")
    }

    private fun attachEMREC2Role() {
        return attachPolicy("arn:aws:iam::aws:policy/service-role/AmazonElasticMapReduceforEC2Role")
    }

    /**
     * Creates an IAM instance profile for EMR EC2 instances
     */
    private fun createInstanceProfile(): String {
        try {
            // Create the instance profile
            val createProfileRequest =
                CreateInstanceProfileRequest.builder()
                    .instanceProfileName(SERVICE_ROLE)
                    .build()

            clients.iam.createInstanceProfile(createProfileRequest)

            // Add role to instance profile
            val addRoleRequest =
                AddRoleToInstanceProfileRequest.builder()
                    .instanceProfileName(SERVICE_ROLE)
                    .roleName(SERVICE_ROLE)
                    .build()

            clients.iam.addRoleToInstanceProfile(addRoleRequest)
        } catch (ignored: EntityAlreadyExistsException) {
            // Instance profile already exists, continue
        }

        return SERVICE_ROLE
    }
}
