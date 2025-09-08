package com.rustyrazorblade.easycasslab.providers.aws

import com.rustyrazorblade.easycasslab.Constants
import com.rustyrazorblade.easycasslab.configuration.User
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.File

/**
 * Manages AWS credentials file creation and access.
 * This was previously handled by the awsConfig lazy property in Context.
 */
class AWSCredentialsManager(private val profileDir: File, private val user: User) {
    private val logger = KotlinLogging.logger {}

    /**
     * The name of the AWS credentials file
     */
    val credentialsFileName: String = Constants.AWS.DEFAULT_CREDENTIALS_NAME

    /**
     * Get or create the AWS credentials file.
     * If the file doesn't exist, it will be created with the user's AWS credentials.
     */
    val credentialsFile: File by lazy {
        val file = File(profileDir, credentialsFileName)
        if (!file.exists()) {
            logger.debug { "Creating AWS credentials file at ${file.absolutePath}" }
            file.writeText(
                """[default]
                |aws_access_key_id=${user.awsAccessKey}
                |aws_secret_access_key=${user.awsSecret}
            """.trimMargin("|"),
            )
        }
        file
    }

    /**
     * Get the absolute path of the credentials file.
     * This is used for volume mounting in Docker containers.
     */
    val credentialsPath: String
        get() = credentialsFile.absolutePath
}
