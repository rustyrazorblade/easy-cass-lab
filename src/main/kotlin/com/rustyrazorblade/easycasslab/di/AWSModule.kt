package com.rustyrazorblade.easycasslab.di

import com.rustyrazorblade.easycasslab.configuration.User
import com.rustyrazorblade.easycasslab.providers.AWS
import com.rustyrazorblade.easycasslab.providers.aws.Clients
import org.koin.dsl.module

/**
 * Koin module for AWS service dependency injection.
 *
 * Provides:
 * - Clients: AWS SDK clients (IAM, EC2, EMR)
 * - AWS: AWS service wrapper for lab environment operations
 *
 * Note: AWSCredentialsManager is no longer registered here - it's created directly by Terraform and
 * Packer classes that need it, since they already have Context.
 */
val awsModule =
    module {
        // Provide AWS Clients as singleton
        single {
            val user = get<User>()
            Clients(user)
        }

        // Provide AWS service as singleton
        single {
            val clients = get<Clients>()
            AWS(clients)
        }
    }
