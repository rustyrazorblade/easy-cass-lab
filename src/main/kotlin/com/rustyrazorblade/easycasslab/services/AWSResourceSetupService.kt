package com.rustyrazorblade.easycasslab.services

import com.rustyrazorblade.easycasslab.configuration.User
import com.rustyrazorblade.easycasslab.configuration.UserConfigProvider
import com.rustyrazorblade.easycasslab.output.OutputHandler
import com.rustyrazorblade.easycasslab.providers.AWS

/**
 * Service responsible for ensuring AWS resources (credentials, S3 bucket, IAM role) are set up
 * before any command runs. This runs automatically in CommandLineParser before command execution.
 */
class AWSResourceSetupService(
    private val aws: AWS,
    private val userConfigProvider: UserConfigProvider,
    private val outputHandler: OutputHandler,
) {
    /**
     * Ensures all AWS resources are set up before any command runs.
     * Only creates resources if they don't exist in user config.
     * Validates credentials first.
     *
     * Performs comprehensive validation of IAM role setup including:
     * - Instance profile existence
     * - Role attachment to instance profile
     * - S3 policy attachment
     *
     * @param userConfig The user configuration to check and update
     * @throws IllegalStateException if resource setup fails validation
     */
    fun ensureAWSResources(userConfig: User) {
        val roleName = AWS.EC2_INSTANCE_ROLE

        // Check if resources already exist and are properly configured
        if (userConfig.s3Bucket.isNotBlank()) {
            val validation = aws.validateRoleSetup(roleName)
            if (validation.isValid) {
                // All resources exist and are properly configured
                return
            }
            // Resources exist in config but validation failed - will attempt to fix
            outputHandler.handleMessage(
                "Warning: IAM role configuration incomplete or invalid. Will attempt to repair.",
            )
            outputHandler.handleMessage("  ${validation.errorMessage}")
        }

        outputHandler.handleMessage("Setting up AWS resources (S3 bucket, IAM role, instance profile)...")

        // Validate AWS credentials before attempting any operations
        try {
            aws.checkPermissions()
        } catch (e: Exception) {
            outputHandler.handleError(
                "AWS credential validation failed. Please check your AWS credentials and permissions.",
                e,
            )
            throw e
        }

        // Generate and create S3 bucket if needed
        val bucketName = User.generateBucketName(userConfig)
        try {
            aws.createS3Bucket(bucketName)
            outputHandler.handleMessage("✓ S3 bucket ready: $bucketName")
        } catch (e: Exception) {
            outputHandler.handleError("Failed to create S3 bucket: $bucketName", e)
            throw e
        }

        // Create IAM role, instance profile, and attach S3 policy
        try {
            aws.createRoleWithS3Policy(roleName, bucketName)
            outputHandler.handleMessage("✓ IAM role and instance profile ready: $roleName")
        } catch (e: software.amazon.awssdk.services.iam.model.IamException) {
            outputHandler.handleError(
                """
                Failed to create IAM role and instance profile.

                This may be due to missing IAM permissions. Please ensure your AWS user has:
                - iam:CreateRole
                - iam:PutRolePolicy
                - iam:CreateInstanceProfile
                - iam:AddRoleToInstanceProfile
                - iam:GetInstanceProfile
                - iam:ListRolePolicies

                You can attach the managed IAM policy 'IAMFullAccess' or create custom inline policies.
                See the IAM policy JSON files in src/main/resources/com/rustyrazorblade/easycasslab/ for examples.
                """.trimIndent(),
                e,
            )
            throw e
        } catch (e: IllegalStateException) {
            // Validation failed after creation
            outputHandler.handleError(
                "IAM role was created but failed validation. This may indicate an AWS propagation delay or configuration issue.",
                e,
            )
            throw e
        } catch (e: Exception) {
            outputHandler.handleError("Unexpected error during IAM role setup", e)
            throw e
        }

        // Final validation before saving config
        val finalValidation = aws.validateRoleSetup(roleName)
        if (!finalValidation.isValid) {
            val errorMsg =
                """
                AWS resource setup completed but final validation failed.

                Status:
                - Instance profile exists: ${finalValidation.instanceProfileExists}
                - Role attached to profile: ${finalValidation.roleAttached}
                - S3 policy attached: ${finalValidation.hasPolicies}

                Error: ${finalValidation.errorMessage}

                This may be due to AWS eventual consistency. Please wait a few seconds and try again.
                """.trimIndent()
            outputHandler.handleError(errorMsg, null)
            throw IllegalStateException(errorMsg)
        }

        // Only update config after successful validation
        userConfig.s3Bucket = bucketName

        // Save updated config
        userConfigProvider.saveUserConfig(userConfig)

        outputHandler.handleMessage("✓ AWS resources setup complete and validated")
    }
}
