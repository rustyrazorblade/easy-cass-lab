package com.rustyrazorblade.easycasslab

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.AwsCredentials
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials
import software.amazon.awssdk.services.ec2.Ec2Client
import software.amazon.awssdk.regions.Region
import java.time.Instant


class EC2(key: String, secret: String, sessionKey: String?, region: Region) {
    val client : Ec2Client

    constructor(key: String, secret: String, region: Region) : this(key, secret, null, region)

    init {
        val creds = credentials(key, secret, sessionKey)
        // TODO: Abstract the provider out
        // tlp cluster should have its own provider that uses the following order:
        // easy-cass-lab config, AWS config
        client = Ec2Client.builder().region(region)
                .credentialsProvider { creds }
                .build()
    }

    fun credentials(key: String, secret: String, sessionKey: String?) : AwsCredentials {
        if (sessionKey != null) {
            return AwsSessionCredentials.create(key, secret, sessionKey)
        }

        return AwsBasicCredentials.create(key, secret)
    }
}