package com.rustyrazorblade.easycasslab.providers

import com.rustyrazorblade.easycasslab.providers.aws.Clients
import software.amazon.awssdk.services.iam.model.*

class AWS(val clients: Clients) {
    companion object {
        val serviceRole = "EasyCassLabServiceRole"
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
                    .roleName(serviceRole)
                    .assumeRolePolicyDocument(assumeRolePolicy)
                    .description("IAM role for EMR service")
                    .build()

            clients.iam.createRole(createRoleRequest)

            attachEMRRole()
        } catch (e: EntityAlreadyExistsException) {
            // Role already exists, continue
        }

        return serviceRole
    }

    private fun attachPolicy(policy: String) {
        val attachPolicyRequest =
            AttachRolePolicyRequest.builder()
                .roleName(serviceRole)
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
                    .instanceProfileName(serviceRole)
                    .build()

            clients.iam.createInstanceProfile(createProfileRequest)

            // Add role to instance profile
            val addRoleRequest =
                AddRoleToInstanceProfileRequest.builder()
                    .instanceProfileName(serviceRole)
                    .roleName(serviceRole)
                    .build()

            clients.iam.addRoleToInstanceProfile(addRoleRequest)
        } catch (e: EntityAlreadyExistsException) {
            // Instance profile already exists, continue
        }

        return serviceRole
    }
}
