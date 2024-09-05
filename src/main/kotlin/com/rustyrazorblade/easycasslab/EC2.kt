package com.rustyrazorblade.easycasslab

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.profiles.ProfileFile
import software.amazon.awssdk.services.ec2.Ec2Client
import software.amazon.awssdk.regions.Region
import java.nio.file.Paths


class EC2(credentialsFile: String, key: String, secret: String, region: Region) {
    val client : Ec2Client

    init {
        client = Ec2Client.builder().region(region)
                .credentialsProvider(credentials(credentialsFile, key, secret))
                .build()
    }

    fun credentials(credentialsFile: String, key: String, secret: String): AwsCredentialsProvider {
        if(credentialsFile.isNotEmpty()) {
            return ProfileCredentialsProvider.builder()
                    .profileName("default")
                    .profileFile(ProfileFile.builder().content(Paths.get(credentialsFile)).type(ProfileFile.Type.CREDENTIALS).build())
                    .build()
        } else {
            return StaticCredentialsProvider.create(AwsBasicCredentials.create(key, secret))
        }

    }
}