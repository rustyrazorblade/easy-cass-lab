package com.rustyrazorblade.easycasslab.providers.aws

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.AwsCredentials
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.ec2.Ec2Client

class EC2(val key: String, val secret: String, val region: Region) : AwsCredentialsProvider {
    val client: Ec2Client

    init {
        val creds = AwsBasicCredentials.create(key, secret)
        // TODO: Abstract the provider out
        // tlp cluster should have its own provider that uses the following order:
        // easy-cass-lab config, AWS config
        client =
            Ec2Client.builder().region(region)
                .credentialsProvider { creds }
                .build()
    }

    override fun resolveCredentials(): AwsCredentials {
        return object : AwsCredentials {
            override fun accessKeyId(): String {
                return key
            }

            override fun secretAccessKey(): String {
                return secret
            }
        }
    }
}
