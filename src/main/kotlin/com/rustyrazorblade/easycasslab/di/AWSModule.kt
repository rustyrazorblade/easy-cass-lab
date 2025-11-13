package com.rustyrazorblade.easycasslab.di

import com.rustyrazorblade.easycasslab.configuration.User
import com.rustyrazorblade.easycasslab.providers.AWS
import com.rustyrazorblade.easycasslab.providers.aws.AMIService
import com.rustyrazorblade.easycasslab.providers.aws.Clients
import com.rustyrazorblade.easycasslab.providers.aws.EC2Service
import org.koin.dsl.module

/**
 * Koin module for AWS service dependency injection.
 *
 * Provides:
 * - Clients: AWS SDK clients (IAM, EC2, EMR)
 * - AWS: AWS service wrapper for lab environment operations
 * - EC2Service: Low-level EC2 AMI operations
 * - AMIService: High-level AMI lifecycle management
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

        // Provide EC2Service as singleton
        single {
            val clients = get<Clients>()
            EC2Service(clients.ec2)
        }

        // Provide AMIService as singleton
        single {
            val ec2Service = get<EC2Service>()
            AMIService(ec2Service)
        }
    }
