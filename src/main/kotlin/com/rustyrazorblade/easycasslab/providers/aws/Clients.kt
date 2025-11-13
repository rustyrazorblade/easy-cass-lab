package com.rustyrazorblade.easycasslab.providers.aws

import com.rustyrazorblade.easycasslab.configuration.User
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.ec2.Ec2Client
import software.amazon.awssdk.services.iam.IamClient

class Clients(
    userConfig: User,
) {
    val region = Region.of(userConfig.region)
    val iam = IamClient.builder().region(region).build()
    val ec2 = Ec2Client.builder().region(region).build()
//    val erm =  EmrClient.builder().region(region).build()
}
