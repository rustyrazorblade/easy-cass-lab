package com.rustyrazorblade.easycasslab.providers

import com.github.ajalt.mordant.TermColors
import com.rustyrazorblade.easycasslab.Constants
import com.rustyrazorblade.easycasslab.configuration.Policy
import com.rustyrazorblade.easycasslab.output.OutputHandler
import io.github.oshai.kotlinlogging.KotlinLogging
import software.amazon.awssdk.core.exception.SdkServiceException
import software.amazon.awssdk.services.iam.IamClient
import software.amazon.awssdk.services.iam.model.AddRoleToInstanceProfileRequest
import software.amazon.awssdk.services.iam.model.AttachRolePolicyRequest
import software.amazon.awssdk.services.iam.model.CreateInstanceProfileRequest
import software.amazon.awssdk.services.iam.model.CreateRoleRequest
import software.amazon.awssdk.services.iam.model.EntityAlreadyExistsException
import software.amazon.awssdk.services.iam.model.IamException
import software.amazon.awssdk.services.iam.model.PutRolePolicyRequest
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.BucketAlreadyExistsException
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException
import software.amazon.awssdk.services.s3.model.CreateBucketRequest
import software.amazon.awssdk.services.s3.model.S3Exception
import software.amazon.awssdk.services.sts.StsClient
import software.amazon.awssdk.services.sts.model.GetCallerIdentityRequest
import software.amazon.awssdk.services.sts.model.StsException

/**
 * AWS infrastructure provider - wraps AWS SDK clients with domain-specific operations.
 *
 * This class provides LOW-LEVEL AWS operations that can be reused across multiple services
 * and commands. It focuses on direct AWS SDK interactions and does NOT contain orchestration
 * logic or complex setup workflows.
 *
 * ## Responsibilities
 *
 * ### AWS SDK Client Management
 * - Manages IAM, S3, and STS client instances
 * - Provides domain-appropriate method signatures
 * - Handles AWS SDK exceptions and error translation
 *
 * ### Account & Permission Operations
 * - Account ID retrieval and caching ([getAccountId])
 * - Credential validation ([checkPermissions])
 * - Permission error handling with helpful user messages ([handlePermissionError])
 *
 * ### IAM Role Operations
 * - IAM role creation with trust policies ([createServiceRole], [createEMREC2Role], [createRoleWithS3Policy])
 * - Policy attachment to roles ([attachPolicy], [attachEMRRole], [attachEMREC2Role])
 * - Instance profile creation and role association
 * - Role validation ([validateRoleSetup], [roleExists])
 *
 * ### S3 Bucket Operations
 * - S3 bucket creation ([createS3Bucket])
 * - Bucket policy application ([putS3BucketPolicy])
 * - Idempotent operations (safe to call multiple times)
 *
 * ### Shared Constants
 * - Standard role names ([EMR_SERVICE_ROLE], [EC2_INSTANCE_ROLE], [EMR_EC2_ROLE])
 * - IAM policy templates with account ID substitution
 *
 * ## NOT Responsible For
 *
 * - **Setup orchestration** - Use [com.rustyrazorblade.easycasslab.services.AWSResourceSetupService]
 * - **Retry logic** - Handled by service layer using resilience4j
 * - **User-facing workflow coordination** - Services handle this
 * - **Configuration management** - Managed by UserConfigProvider
 * - **Complex validation workflows** - Delegated to services
 *
 * ## Usage Guidelines
 *
 * ### When to Add Methods Here
 * - Direct AWS SDK operations (create, get, attach, delete)
 * - Operations needed by multiple services or commands
 * - Infrastructure-level operations without business logic
 * - Error handling for AWS-specific exceptions
 *
 * ### When to Use Service Layer Instead
 * - Multi-step workflows requiring coordination
 * - Operations requiring retry logic or resilience
 * - User-facing setup or provisioning workflows
 * - Operations that modify user configuration
 * - Complex validation requiring multiple checks
 *
 * ## Relationship with AWSResourceSetupService
 *
 * - **AWS** (this class): Infrastructure layer - "how to talk to AWS"
 * - **AWSResourceSetupService**: Service layer - "how to set up resources"
 *
 * The service layer uses this class for low-level operations while adding:
 * - Orchestration logic (order of operations)
 * - Retry and error recovery
 * - User messaging and output
 * - Configuration updates
 * - Validation workflows
 *
 * ## Testing
 *
 * This class is mocked by default in [com.rustyrazorblade.easycasslab.BaseKoinTest],
 * allowing tests to verify behavior without making real AWS API calls.
 *
 * @property iamClient AWS IAM client for identity and access management operations
 * @property s3Client AWS S3 client for bucket operations
 * @property stsClient AWS STS client for credential and account operations
 * @property outputHandler Handler for user-facing messages and error output
 */
class AWS(
    private val iamClient: IamClient,
    private val s3Client: S3Client,
    private val stsClient: StsClient,
    private val outputHandler: OutputHandler,
) {
    private var cachedAccountId: String? = null

    companion object {
        const val EMR_SERVICE_ROLE = "EasyCassLabEMRServiceRole"
        const val EMR_EC2_ROLE = "EasyCassLabEMREC2Role"
        const val EC2_INSTANCE_ROLE = "EasyCassLabEC2Role"
        private val log = KotlinLogging.logger {}

        /**
         * Loads the required IAM policies from resources with account ID substitution.
         * Returns a list of Policy objects with names and content for all three required policies.
         *
         * @param accountId The AWS account ID to substitute for ACCOUNT_ID placeholder
         */
        private fun getRequiredIAMPolicies(accountId: String): List<Policy> {
            val policyData =
                listOf(
                    "iam-policy-ec2.json" to "EasyCassLabEC2",
                    "iam-policy-iam-s3.json" to "EasyCassLabIAM",
                    "iam-policy-emr.json" to "EasyCassLabEMR",
                )

            return policyData.map { (fileName, policyName) ->
                val policyStream = AWS::class.java.getResourceAsStream("/com/rustyrazorblade/easycasslab/$fileName")
                val policyContent =
                    policyStream?.bufferedReader()?.use { it.readText() }
                        ?: error("Unable to load IAM policy template: $fileName")

                // Replace ACCOUNT_ID placeholder with actual account ID
                val processedContent = policyContent.replace("ACCOUNT_ID", accountId)

                Policy(name = policyName, body = processedContent)
            }
        }
    }

    /**
     * Displays helpful error message when AWS permission is denied
     */
    private fun handlePermissionError(
        exception: SdkServiceException,
        operation: String,
    ) {
        with(TermColors()) {
            outputHandler.handleMessage(
                """
                |
                |========================================
                |AWS PERMISSION ERROR
                |========================================
                |
                |Operation: $operation
                |Error: ${exception.message}
                |
                |To fix this issue, add the following IAM policies to your AWS user.
                |You need to create THREE separate inline policies:
                |
                """.trimMargin(),
            )

            // Try to get account ID, fallback to placeholder if that fails too
            val accountId =
                try {
                    getAccountId()
                } catch (e: Exception) {
                    outputHandler.handleMessage(
                        """
                        |NOTE: Replace ACCOUNT_ID in the policies below with your AWS account ID.
                        |You can find your account ID in the error message above (the 12-digit number in the ARN).
                        |
                        """.trimMargin(),
                    )
                    "ACCOUNT_ID"
                }

            val policies = getRequiredIAMPolicies(accountId)
            policies.forEachIndexed { index, policy ->
                outputHandler.handleMessage(
                    """
                    |${green("========================================")}
                    |${green("Policy ${index + 1}: ${policy.name}")}
                    |${green("========================================")}
                    |
                    |${policy.body}
                    |
                    """.trimMargin(),
                )
            }

            outputHandler.handleMessage(
                """
                |========================================
                |
                |RECOMMENDED: Create managed policies and attach to a group
                |  • No size limits (inline policies limited to 5,120 bytes total)
                |  • Required for EMR/Spark cluster functionality
                |  • Reusable across multiple users
                |
                |To apply these policies:
                |  1. Go to AWS IAM Console (https://console.aws.amazon.com/iam/)
                |  2. Create IAM group (e.g., "EasyCassLabUsers")
                |  3. Create three managed policies:
                |     - Policies → Create Policy → JSON tab
                |     - Paste policy content and name: EasyCassLabEC2, EasyCassLabIAM, EasyCassLabEMR
                |  4. Attach policies to your group
                |  5. Add your IAM user to the group
                |
                |ALTERNATIVE (single user): Attach as inline policies to your user
                |  WARNING: May hit 5,120 byte limit with all three policies
                |
                |========================================
                |
                """.trimMargin(),
            )
        }
    }

    /**
     * Retrieves the AWS account ID for the authenticated credentials.
     * Caches the result to avoid repeated STS API calls.
     *
     * @return The AWS account ID
     * @throws StsException if unable to retrieve account information
     */
    fun getAccountId(): String {
        if (cachedAccountId == null) {
            val response =
                stsClient.getCallerIdentity(
                    GetCallerIdentityRequest.builder().build(),
                )
            cachedAccountId = response.account()
            log.debug { "Retrieved and cached account ID: $cachedAccountId" }
        }
        return cachedAccountId!!
    }

    /**
     * Validates AWS credentials by calling STS GetCallerIdentity.
     * This ensures credentials are valid and the user can authenticate with AWS.
     * Displays account ID and user ARN for verification.
     *
     * @throws StsException if credentials are invalid or lack permissions
     * @throws SdkServiceException if there's a service error
     */
    fun checkPermissions() {
        try {
            val request = GetCallerIdentityRequest.builder().build()
            val response = stsClient.getCallerIdentity(request)

            // Cache the account ID for later use
            cachedAccountId = response.account()

            log.info { "AWS credentials validated successfully" }
            log.info { "Account: ${response.account()}" }
            log.info { "User ARN: ${response.arn()}" }
            log.info { "User ID: ${response.userId()}" }

            outputHandler.handleMessage(
                """
                |AWS credentials validated:
                |  Account: ${response.account()}
                |  ARN: ${response.arn()}
                |  User ID: ${response.userId()}
                """.trimMargin(),
            )
        } catch (e: StsException) {
            log.error(e) { "AWS credential validation failed" }

            // Check if this is an authorization error (403) vs authentication error (401/other)
            if (e.statusCode() == Constants.HttpStatus.FORBIDDEN) {
                // Permission denied - credentials are valid but lack permissions
                handlePermissionError(e, "STS GetCallerIdentity")
            } else {
                // Authentication error - invalid or missing credentials
                with(TermColors()) {
                    outputHandler.handleMessage(
                        """
                        |
                        |========================================
                        |AWS CREDENTIAL ERROR
                        |========================================
                        |
                        |Unable to validate AWS credentials: ${e.message}
                        |
                        |This usually means:
                        |  - AWS credentials are not configured
                        |  - AWS credentials have expired
                        |  - AWS access key or secret key is invalid
                        |
                        |To fix this issue, you need to configure valid AWS credentials.
                        |
                        |You can configure credentials using one of these methods:
                        |  1. AWS CLI: Run 'aws configure' and enter your credentials
                        |  2. Environment variables: Set AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY
                        |  3. AWS credentials file: Create ~/.aws/credentials with your credentials
                        |
                        |To verify your credentials are working, run:
                        |  aws sts get-caller-identity
                        |
                        |For full AWS permissions required by easy-cass-lab, add THREE inline policies:
                        |
                        """.trimMargin(),
                    )

                    val policies = getRequiredIAMPolicies("ACCOUNT_ID")
                    policies.forEachIndexed { index, policy ->
                        outputHandler.handleMessage(
                            """
                            |${green("========================================")}
                            |${green("Policy ${index + 1}: ${policy.name}")}
                            |${green("========================================")}
                            |
                            |${policy.body}
                            |
                            """.trimMargin(),
                        )
                    }

                    outputHandler.handleMessage(
                        """
                        |========================================
                        |
                        """.trimMargin(),
                    )
                }
            }

            // Rethrow the exception to be handled by main()
            throw e
        } catch (e: SdkServiceException) {
            log.error(e) { "AWS service error during credential validation" }
            handlePermissionError(e, "STS GetCallerIdentity")
            // Rethrow the exception to be handled by main()
            throw e
        }
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
                CreateRoleRequest
                    .builder()
                    .roleName(EMR_SERVICE_ROLE)
                    .assumeRolePolicyDocument(assumeRolePolicy)
                    .description("IAM role for EMR service")
                    .build()

            iamClient.createRole(createRoleRequest)

            attachEMRRole()
        } catch (ignored: EntityAlreadyExistsException) {
            // Role already exists, continue
        }

        return EMR_SERVICE_ROLE
    }

    private fun attachPolicy(
        roleName: String,
        policy: String,
    ) {
        val attachPolicyRequest =
            AttachRolePolicyRequest
                .builder()
                .roleName(roleName)
                .policyArn(policy)
                .build()
        iamClient.attachRolePolicy(attachPolicyRequest)
    }

    private fun attachEMRRole() {
        // Attach necessary managed policy
        return attachPolicy(EMR_SERVICE_ROLE, "arn:aws:iam::aws:policy/service-role/AmazonElasticMapReduceRole")
    }

    private fun attachEMREC2Role() =
        attachPolicy(
            EMR_EC2_ROLE,
            "arn:aws:iam::aws:policy/service-role/AmazonElasticMapReduceforEC2Role",
        )

    /**
     * Creates an IAM role and instance profile for EMR EC2 instances
     */
    fun createEMREC2Role(): String {
        // EC2 assume role policy
        val assumeRolePolicy = """{
            "Version": "2012-10-17",
            "Statement": [
                {
                    "Effect": "Allow",
                    "Principal": {
                        "Service": "ec2.amazonaws.com"
                    },
                    "Action": "sts:AssumeRole"
                }
            ]
        }"""

        try {
            // Create the IAM role
            val createRoleRequest =
                CreateRoleRequest
                    .builder()
                    .roleName(EMR_EC2_ROLE)
                    .assumeRolePolicyDocument(assumeRolePolicy)
                    .description("IAM role for EMR EC2 instances")
                    .build()

            iamClient.createRole(createRoleRequest)
            log.info { "Created IAM role: $EMR_EC2_ROLE" }

            // Attach EMR EC2 managed policy
            attachEMREC2Role()
        } catch (e: EntityAlreadyExistsException) {
            log.info { "IAM role already exists: $EMR_EC2_ROLE" }
        }

        // Create instance profile
        try {
            val createProfileRequest =
                CreateInstanceProfileRequest
                    .builder()
                    .instanceProfileName(EMR_EC2_ROLE)
                    .build()

            iamClient.createInstanceProfile(createProfileRequest)

            // Add role to instance profile
            val addRoleRequest =
                AddRoleToInstanceProfileRequest
                    .builder()
                    .instanceProfileName(EMR_EC2_ROLE)
                    .roleName(EMR_EC2_ROLE)
                    .build()

            iamClient.addRoleToInstanceProfile(addRoleRequest)
            log.info { "Created instance profile for: $EMR_EC2_ROLE" }
        } catch (ignored: EntityAlreadyExistsException) {
            log.info { "Instance profile already exists: $EMR_EC2_ROLE" }
        }

        return EMR_EC2_ROLE
    }

    /**
     * Creates an S3 bucket with the specified name.
     * Idempotent - will succeed even if bucket already exists and is owned by you.
     *
     * @param bucketName The name of the bucket to create. Must be valid S3 bucket name (lowercase, 3-63 chars).
     * @return The bucket name
     * @throws IllegalArgumentException if bucket name is invalid
     */
    fun createS3Bucket(bucketName: String): String {
        require(bucketName.matches(Regex("^[a-z0-9][a-z0-9-]{1,61}[a-z0-9]$"))) {
            "Invalid S3 bucket name: $bucketName. Must be lowercase, 3-63 chars, alphanumeric and hyphens only."
        }

        try {
            val request =
                CreateBucketRequest
                    .builder()
                    .bucket(bucketName)
                    .build()

            s3Client.createBucket(request)
            log.info { "Created S3 bucket: $bucketName" }
        } catch (e: BucketAlreadyOwnedByYouException) {
            log.info { "S3 bucket already exists and is owned by you: $bucketName" }
        } catch (e: BucketAlreadyExistsException) {
            log.info { "S3 bucket already exists: $bucketName" }
        } catch (e: S3Exception) {
            if (e.statusCode() == Constants.HttpStatus.FORBIDDEN) {
                handlePermissionError(e, "S3 CreateBucket")
            }
            throw e
        }

        return bucketName
    }

    /**
     * Creates and applies an S3 bucket policy that grants access to all easy-cass-lab IAM roles.
     * Idempotent - will succeed even if policy already exists.
     *
     * The policy grants full S3 access to:
     * - EasyCassLabEC2Role (EC2 instances)
     * - EasyCassLabEMRServiceRole (EMR service)
     * - EasyCassLabEMREC2Role (EMR EC2 instances)
     *
     * @param bucketName The S3 bucket name to apply the policy to
     * @throws IllegalArgumentException if bucket name is invalid
     * @throws S3Exception if S3 operations fail
     */
    fun putS3BucketPolicy(bucketName: String) {
        require(bucketName.matches(Regex("^[a-z0-9][a-z0-9-]{1,61}[a-z0-9]$"))) {
            "Invalid S3 bucket name: $bucketName. Must be lowercase, 3-63 chars, alphanumeric and hyphens only."
        }

        val accountId = getAccountId()

        val bucketPolicy =
            """
            {
                "Version": "2012-10-17",
                "Statement": [
                    {
                        "Effect": "Allow",
                        "Principal": {
                            "AWS": [
                                "arn:aws:iam::$accountId:role/$EC2_INSTANCE_ROLE",
                                "arn:aws:iam::$accountId:role/$EMR_SERVICE_ROLE",
                                "arn:aws:iam::$accountId:role/$EMR_EC2_ROLE"
                            ]
                        },
                        "Action": "s3:*",
                        "Resource": [
                            "arn:aws:s3:::$bucketName",
                            "arn:aws:s3:::$bucketName/*"
                        ]
                    }
                ]
            }
            """.trimIndent()

        try {
            val request =
                software.amazon.awssdk.services.s3.model.PutBucketPolicyRequest
                    .builder()
                    .bucket(bucketName)
                    .policy(bucketPolicy)
                    .build()

            s3Client.putBucketPolicy(request)
            log.info { "✓ Applied S3 bucket policy granting access to all 3 IAM roles: $bucketName" }
        } catch (e: software.amazon.awssdk.services.s3.model.S3Exception) {
            if (e.statusCode() == Constants.HttpStatus.FORBIDDEN) {
                handlePermissionError(e, "S3 PutBucketPolicy")
            }
            log.error { "Failed to apply S3 bucket policy: $bucketName - ${e.message}" }
            throw e
        }
    }

    /**
     * Checks if an IAM role exists.
     *
     * @param roleName The name of the IAM role to check
     * @return true if the role exists, false otherwise
     */
    fun roleExists(roleName: String): Boolean =
        try {
            val request =
                software.amazon.awssdk.services.iam.model.GetInstanceProfileRequest
                    .builder()
                    .instanceProfileName(roleName)
                    .build()
            iamClient.getInstanceProfile(request)
            true
        } catch (e: software.amazon.awssdk.services.iam.model.NoSuchEntityException) {
            false
        } catch (e: software.amazon.awssdk.services.iam.model.IamException) {
            if (e.statusCode() == Constants.HttpStatus.FORBIDDEN) {
                handlePermissionError(e, "IAM GetInstanceProfile")
            }
            throw e
        }

    /**
     * Data class representing the validation result for IAM role setup.
     *
     * @property isValid True if all required components exist and are properly configured
     * @property instanceProfileExists True if the instance profile exists
     * @property roleAttached True if the role is attached to the instance profile
     * @property hasPolicies True if the role has inline policies attached
     * @property errorMessage Optional error message if validation failed
     */
    data class RoleValidationResult(
        val isValid: Boolean,
        val instanceProfileExists: Boolean,
        val roleAttached: Boolean,
        val hasPolicies: Boolean,
        val errorMessage: String? = null,
    )

    /**
     * Validates that the IAM role is properly set up with instance profile and policies.
     *
     * Checks that:
     * 1. Instance profile exists
     * 2. Role is attached to the instance profile
     * 3. Role has inline policies (S3 policy)
     *
     * @param roleName The name of the IAM role and instance profile to validate
     * @return RoleValidationResult with detailed validation status
     */
    fun validateRoleSetup(roleName: String): RoleValidationResult {
        try {
            // Check if instance profile exists and has role attached
            val getProfileRequest =
                software.amazon.awssdk.services.iam.model.GetInstanceProfileRequest
                    .builder()
                    .instanceProfileName(roleName)
                    .build()

            val profileResponse =
                try {
                    iamClient.getInstanceProfile(getProfileRequest)
                } catch (e: software.amazon.awssdk.services.iam.model.NoSuchEntityException) {
                    return RoleValidationResult(
                        isValid = false,
                        instanceProfileExists = false,
                        roleAttached = false,
                        hasPolicies = false,
                        errorMessage = "Instance profile '$roleName' does not exist",
                    )
                }

            // Check if role is attached to instance profile
            val roles = profileResponse.instanceProfile().roles()
            val roleAttached = roles.any { it.roleName() == roleName }

            if (!roleAttached) {
                return RoleValidationResult(
                    isValid = false,
                    instanceProfileExists = true,
                    roleAttached = false,
                    hasPolicies = false,
                    errorMessage = "Role '$roleName' is not attached to instance profile",
                )
            }

            // Check if role has inline policies
            val listPoliciesRequest =
                software.amazon.awssdk.services.iam.model.ListRolePoliciesRequest
                    .builder()
                    .roleName(roleName)
                    .build()

            val policiesResponse = iamClient.listRolePolicies(listPoliciesRequest)
            val hasPolicies = policiesResponse.policyNames().isNotEmpty()

            return RoleValidationResult(
                isValid = hasPolicies,
                instanceProfileExists = true,
                roleAttached = true,
                hasPolicies = hasPolicies,
                errorMessage =
                    if (!hasPolicies) {
                        "Role '$roleName' exists but has no inline policies"
                    } else {
                        null
                    },
            )
        } catch (e: software.amazon.awssdk.services.iam.model.IamException) {
            if (e.statusCode() == Constants.HttpStatus.FORBIDDEN) {
                handlePermissionError(e, "IAM ValidateRole")
            }
            return RoleValidationResult(
                isValid = false,
                instanceProfileExists = false,
                roleAttached = false,
                hasPolicies = false,
                errorMessage = "IAM error: ${e.message}",
            )
        }
    }

    /**
     * Creates an IAM role with EC2 trust policy and attaches an inline S3 policy granting full access to the specified bucket.
     * Idempotent - will succeed even if role already exists.
     *
     * Uses retry logic with exponential backoff for instance profile creation to handle AWS eventual consistency.
     *
     * @param roleName The name of the IAM role to create
     * @param bucketName The S3 bucket name to grant access to
     * @return The role name
     * @throws IllegalArgumentException if role name is invalid
     * @throws IamException if IAM operations fail after retries
     */
    fun createRoleWithS3Policy(
        roleName: String,
        bucketName: String,
    ): String {
        require(roleName.matches(Regex("^[\\w+=,.@-]{1,64}$"))) {
            "Invalid IAM role name: $roleName. Must be 1-64 chars, alphanumeric plus +=,.@-_ only."
        }

        log.info { "Setting up IAM role and instance profile: $roleName" }

        // EC2 assume role policy
        val assumeRolePolicy = """{
            "Version": "2012-10-17",
            "Statement": [
                {
                    "Effect": "Allow",
                    "Principal": {
                        "Service": "ec2.amazonaws.com"
                    },
                    "Action": "sts:AssumeRole"
                }
            ]
        }"""

        // Step 1: Create IAM role
        try {
            val createRoleRequest =
                CreateRoleRequest
                    .builder()
                    .roleName(roleName)
                    .assumeRolePolicyDocument(assumeRolePolicy)
                    .description("IAM role for easy-cass-lab with S3 access")
                    .build()

            iamClient.createRole(createRoleRequest)
            log.info { "✓ Created IAM role: $roleName" }
        } catch (e: EntityAlreadyExistsException) {
            log.info { "✓ IAM role already exists: $roleName" }
        } catch (e: IamException) {
            if (e.statusCode() == Constants.HttpStatus.FORBIDDEN) {
                handlePermissionError(e, "IAM CreateRole")
            }
            log.error { "Failed to create IAM role: $roleName - ${e.message}" }
            throw e
        }

        // Step 2: Attach S3 access policy
        val s3Policy = """{
            "Version": "2012-10-17",
            "Statement": [
                {
                    "Effect": "Allow",
                    "Action": "s3:*",
                    "Resource": [
                        "arn:aws:s3:::$bucketName",
                        "arn:aws:s3:::$bucketName/*"
                    ]
                }
            ]
        }"""

        try {
            val putPolicyRequest =
                PutRolePolicyRequest
                    .builder()
                    .roleName(roleName)
                    .policyName("S3Access")
                    .policyDocument(s3Policy)
                    .build()

            iamClient.putRolePolicy(putPolicyRequest)
            log.info { "✓ Attached S3 access policy to role: $roleName for bucket: $bucketName" }
        } catch (e: IamException) {
            if (e.statusCode() == Constants.HttpStatus.FORBIDDEN) {
                handlePermissionError(e, "IAM PutRolePolicy")
            }
            log.error { "Failed to attach S3 policy to role: $roleName - ${e.message}" }
            throw e
        }

        // Step 3: Create instance profile with retry logic for AWS eventual consistency
        val retryConfig =
            io.github.resilience4j.retry.RetryConfig
                .custom<Unit>()
                .maxAttempts(Constants.Retry.MAX_INSTANCE_PROFILE_RETRIES)
                .intervalFunction { attemptCount ->
                    // Exponential backoff: 1s, 2s, 4s, 8s, 16s
                    Constants.Retry.EXPONENTIAL_BACKOFF_BASE_MS * (1L shl (attemptCount - 1))
                }.retryOnException { throwable ->
                    when {
                        throwable !is IamException -> false
                        throwable is EntityAlreadyExistsException -> false // Don't retry if already exists
                        throwable.statusCode() == Constants.HttpStatus.FORBIDDEN -> {
                            log.warn { "Permission denied for instance profile creation - will not retry" }
                            false
                        }
                        throwable.statusCode() in Constants.HttpStatus.SERVER_ERROR_MIN..Constants.HttpStatus.SERVER_ERROR_MAX -> {
                            log.warn { "AWS service error ${throwable.statusCode()} - will retry instance profile creation" }
                            true
                        }
                        throwable.statusCode() == Constants.HttpStatus.NOT_FOUND -> {
                            log.warn { "Role not found (eventual consistency) - will retry instance profile creation" }
                            true
                        }
                        else -> {
                            log.warn { "IAM error during instance profile creation: ${throwable.message} - will retry" }
                            true
                        }
                    }
                }.build()

        val retry =
            io.github.resilience4j.retry.Retry
                .of("createInstanceProfile-$roleName", retryConfig)

        try {
            io.github.resilience4j.retry.Retry
                .decorateRunnable(retry) {
                    try {
                        // Create instance profile
                        val createProfileRequest =
                            CreateInstanceProfileRequest
                                .builder()
                                .instanceProfileName(roleName)
                                .build()

                        iamClient.createInstanceProfile(createProfileRequest)
                        log.info { "✓ Created instance profile: $roleName" }
                    } catch (e: EntityAlreadyExistsException) {
                        log.info { "✓ Instance profile already exists: $roleName" }
                    }

                    // Add role to instance profile
                    try {
                        val addRoleRequest =
                            AddRoleToInstanceProfileRequest
                                .builder()
                                .instanceProfileName(roleName)
                                .roleName(roleName)
                                .build()

                        iamClient.addRoleToInstanceProfile(addRoleRequest)
                        log.info { "✓ Added role to instance profile: $roleName" }
                    } catch (e: software.amazon.awssdk.services.iam.model.LimitExceededException) {
                        // Role already in instance profile - this is OK
                        log.info { "✓ Role already attached to instance profile: $roleName" }
                    }
                }.run()
        } catch (e: IamException) {
            if (e.statusCode() == Constants.HttpStatus.FORBIDDEN) {
                handlePermissionError(e, "IAM CreateInstanceProfile/AddRoleToInstanceProfile")
            }
            log.error { "Failed to create instance profile after retries: $roleName - ${e.message}" }
            throw e
        }

        // Step 4: Validate the complete setup
        log.info { "Validating IAM role setup: $roleName" }
        val validation = validateRoleSetup(roleName)
        if (!validation.isValid) {
            val errorMsg =
                "IAM role setup validation failed: ${validation.errorMessage}\n" +
                    "  - Instance profile exists: ${validation.instanceProfileExists}\n" +
                    "  - Role attached: ${validation.roleAttached}\n" +
                    "  - Has policies: ${validation.hasPolicies}"
            log.error { errorMsg }
            throw IllegalStateException(errorMsg)
        }

        log.info { "✓ IAM role setup complete and validated: $roleName" }
        return roleName
    }
}
