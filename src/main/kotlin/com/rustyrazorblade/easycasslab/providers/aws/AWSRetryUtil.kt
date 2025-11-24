package com.rustyrazorblade.easycasslab.providers.aws

import com.rustyrazorblade.easycasslab.Constants
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.resilience4j.retry.RetryConfig
import software.amazon.awssdk.awscore.exception.AwsServiceException
import software.amazon.awssdk.services.iam.model.EntityAlreadyExistsException
import software.amazon.awssdk.services.iam.model.IamException

/**
 * Utility for creating standardized retry configurations for AWS operations.
 *
 * Provides pre-configured retry policies for IAM and S3 operations with:
 * - Exponential backoff (1s, 2s, 4s, 8s, ...)
 * - Service-specific retry logic
 * - Proper exception handling for AWS eventual consistency
 *
 * This consolidates retry logic to ensure consistent behavior across AWS infrastructure operations.
 */
object AWSRetryUtil {
    private val log = KotlinLogging.logger {}

    /**
     * Creates retry configuration for IAM operations.
     *
     * IAM operations require special handling due to AWS eventual consistency:
     * - Higher retry count (5 attempts) to handle propagation delays
     * - Retries on 404 (not found) errors as resources may not be visible immediately
     * - Retries on 5xx server errors
     * - Does NOT retry on 403 (forbidden) as these are permission errors
     * - Does NOT retry on EntityAlreadyExistsException
     *
     * Exponential backoff: 1s, 2s, 4s, 8s, 16s
     *
     * @return RetryConfig configured for IAM operations
     */
    fun createIAMRetryConfig(): RetryConfig =
        RetryConfig
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
                        log.warn { "Permission denied for IAM operation - will not retry" }
                        false
                    }
                    throwable.statusCode() in
                        Constants.HttpStatus.SERVER_ERROR_MIN..Constants.HttpStatus.SERVER_ERROR_MAX -> {
                        log.warn {
                            "AWS service error ${throwable.statusCode()} for IAM operation - will retry"
                        }
                        true
                    }
                    throwable.statusCode() == Constants.HttpStatus.NOT_FOUND -> {
                        log.warn { "Resource not found (eventual consistency) - will retry IAM operation" }
                        true
                    }
                    else -> {
                        log.warn { "IAM error: ${throwable.message} - will retry" }
                        true
                    }
                }
            }.build()

    /**
     * Creates retry configuration for S3 operations.
     *
     * S3 operations have simpler retry logic:
     * - Standard retry count (3 attempts)
     * - Retries only on 5xx server errors
     * - Does NOT retry on 4xx client errors
     *
     * Exponential backoff: 1s, 2s, 4s
     *
     * @return RetryConfig configured for S3 operations
     */
    fun <T> createS3RetryConfig(): RetryConfig =
        RetryConfig
            .custom<T>()
            .maxAttempts(Constants.Retry.MAX_S3_RETRIES)
            .intervalFunction { attemptCount ->
                // Exponential backoff: 1s, 2s, 4s
                Constants.Retry.EXPONENTIAL_BACKOFF_BASE_MS * (1L shl (attemptCount - 1))
            }.retryOnException { throwable ->
                // Retry on AWS service errors (5xx), but not on client errors (4xx)
                throwable is AwsServiceException &&
                    throwable.statusCode() >= Constants.HttpStatus.CLIENT_ERROR_THRESHOLD
            }.build()
}
