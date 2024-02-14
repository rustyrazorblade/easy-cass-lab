package com.rustyrazorblade.easycasslab

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.services.ec2.Ec2Client
import software.amazon.awssdk.regions.Region


class EC2(key: String, secret: String, region: Region) {
    val client : Ec2Client

    init {
        val creds = AwsBasicCredentials.create(key, secret)
        // TODO: Abstract the provider out
        // tlp cluster should have its own provider that uses the following order:
        // easy-cass-lab config, AWS config
        client = Ec2Client.builder().region(region)
                .credentialsProvider { creds }
                .build()
    }

    fun isInstanceStore(instanceType: String) : Boolean {

        return true
    }
}