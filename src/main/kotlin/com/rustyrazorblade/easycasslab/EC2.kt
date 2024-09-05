package com.rustyrazorblade.easycasslab

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.AwsCredentials
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials
import software.amazon.awssdk.services.ec2.Ec2Client
import software.amazon.awssdk.regions.Region
import java.time.Instant


class EC2(key: String, secret: String, sessionKey: String?, sessionExpiry: Instant?, region: Region) {
    val client : Ec2Client

    constructor(key: String, secret: String, region: Region) : this(key, secret, null, null, region)

    init {
        val creds = credentials(key, secret, sessionKey, sessionExpiry)
        // TODO: Abstract the provider out
        // tlp cluster should have its own provider that uses the following order:
        // easy-cass-lab config, AWS config
        client = Ec2Client.builder().region(region)
                .credentialsProvider { creds }
                .build()
    }

    fun credentials(key: String, secret: String, sessionKey: String?, sessionExpiry: Instant?) : AwsCredentials {
        if (sessionKey != null && sessionExpiry != null) {
            return AwsSessionCredentials.builder().accessKeyId(key)
                    .secretAccessKey(secret)
                    .sessionToken(sessionKey)
                    .expirationTime(sessionExpiry)
                    .build()
        }

        return AwsBasicCredentials.create(key, secret)
    }
}