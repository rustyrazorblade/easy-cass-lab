package com.rustyrazorblade.easycasslab.providers.aws

import com.rustyrazorblade.easycasslab.Constants
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import software.amazon.awssdk.awscore.exception.AwsServiceException
import software.amazon.awssdk.services.iam.model.EntityAlreadyExistsException
import software.amazon.awssdk.services.iam.model.IamException

/**
 * Tests for AWSRetryUtil retry configuration builders.
 *
 * Validates that retry configurations:
 * - Have appropriate max attempts
 * - Retry on expected exception types
 * - Do not retry on forbidden (403) errors
 * - Do not retry on EntityAlreadyExistsException
 */
class AWSRetryUtilTest {
    @Test
    fun `createIAMRetryConfig should use correct max attempts from constants`() {
        val config = AWSRetryUtil.createIAMRetryConfig()

        assertThat(config.maxAttempts).isEqualTo(Constants.Retry.MAX_INSTANCE_PROFILE_RETRIES)
    }

    @Test
    fun `createIAMRetryConfig should not retry on EntityAlreadyExistsException`() {
        val config = AWSRetryUtil.createIAMRetryConfig()

        val exception = EntityAlreadyExistsException.builder().message("Already exists").build()

        assertThat(config.exceptionPredicate.test(exception)).isFalse()
    }

    @Test
    fun `createIAMRetryConfig should not retry on forbidden errors`() {
        val config = AWSRetryUtil.createIAMRetryConfig()

        val forbiddenException =
            IamException
                .builder()
                .message("Forbidden")
                .statusCode(Constants.HttpStatus.FORBIDDEN)
                .build()

        assertThat(config.exceptionPredicate.test(forbiddenException)).isFalse()
    }

    @Test
    fun `createIAMRetryConfig should retry on 5xx server errors`() {
        val config = AWSRetryUtil.createIAMRetryConfig()

        val serverError =
            IamException
                .builder()
                .message("Service unavailable")
                .statusCode(503)
                .build()

        assertThat(config.exceptionPredicate.test(serverError)).isTrue()
    }

    @Test
    fun `createIAMRetryConfig should retry on 404 not found (eventual consistency)`() {
        val config = AWSRetryUtil.createIAMRetryConfig()

        val notFoundException =
            IamException
                .builder()
                .message("Not found")
                .statusCode(Constants.HttpStatus.NOT_FOUND)
                .build()

        assertThat(config.exceptionPredicate.test(notFoundException)).isTrue()
    }

    @Test
    fun `createIAMRetryConfig should retry on other IAM exceptions`() {
        val config = AWSRetryUtil.createIAMRetryConfig()

        val iamException =
            IamException
                .builder()
                .message("Some IAM error")
                .statusCode(400)
                .build()

        assertThat(config.exceptionPredicate.test(iamException)).isTrue()
    }

    @Test
    fun `createIAMRetryConfig should not retry on non-IAM exceptions`() {
        val config = AWSRetryUtil.createIAMRetryConfig()

        val exception = RuntimeException("Some error")

        assertThat(config.exceptionPredicate.test(exception)).isFalse()
    }

    @Test
    fun `createS3RetryConfig should use correct max attempts from constants`() {
        val config = AWSRetryUtil.createS3RetryConfig<Unit>()

        assertThat(config.maxAttempts).isEqualTo(Constants.Retry.MAX_S3_RETRIES)
    }

    @Test
    fun `createS3RetryConfig should retry on AWS service errors 5xx`() {
        val config = AWSRetryUtil.createS3RetryConfig<Unit>()

        val serverError =
            AwsServiceException
                .builder()
                .message("Service unavailable")
                .statusCode(503)
                .build()

        assertThat(config.exceptionPredicate.test(serverError)).isTrue()
    }

    @Test
    fun `createS3RetryConfig should not retry on client errors 4xx`() {
        val config = AWSRetryUtil.createS3RetryConfig<Unit>()

        val clientError =
            AwsServiceException
                .builder()
                .message("Bad request")
                .statusCode(400)
                .build()

        assertThat(config.exceptionPredicate.test(clientError)).isFalse()
    }

    @Test
    fun `createS3RetryConfig should not retry on non-AWS exceptions`() {
        val config = AWSRetryUtil.createS3RetryConfig<Unit>()

        val exception = RuntimeException("Some error")

        assertThat(config.exceptionPredicate.test(exception)).isFalse()
    }
}
