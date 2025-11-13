package com.rustyrazorblade.easycasslab.di

import com.rustyrazorblade.easycasslab.configuration.User
import com.rustyrazorblade.easycasslab.providers.AWS
import com.rustyrazorblade.easycasslab.providers.aws.AMIService
import com.rustyrazorblade.easycasslab.providers.aws.EC2Service
import org.koin.dsl.module
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.ec2.Ec2Client
import software.amazon.awssdk.services.iam.IamClient

/**
 * Koin module for AWS service dependency injection.
 *
 * Provides:
 * - Region: AWS region configuration
 * - IamClient: AWS IAM client for identity and access management
 * - Ec2Client: AWS EC2 client for instance and AMI management
 * - AWS: AWS service wrapper for lab environment operations
 * - EC2Service: Low-level EC2 AMI operations
 * - AMIService: High-level AMI lifecycle management
 *
 * Note: AWSCredentialsManager is no longer registered here - it's created directly by Terraform and
 * Packer classes that need it, since they already have Context.
 */
val awsModule =
    module {
        // Provide AWS region as singleton
        single { Region.of(get<User>().region) }

        // Provide AWS SDK clients as singletons
        single {
            IamClient
                .builder()
                .region(get<Region>())
                .build()
        }

        single {
            Ec2Client
                .builder()
                .region(get<Region>())
                .build()
        }

        // Provide AWS service as singleton
        single { AWS(get<IamClient>()) }

        // Provide EC2Service as singleton
        single { EC2Service(get<Ec2Client>()) }

        // Provide AMIService as singleton
        single { AMIService(get<EC2Service>()) }
    }
