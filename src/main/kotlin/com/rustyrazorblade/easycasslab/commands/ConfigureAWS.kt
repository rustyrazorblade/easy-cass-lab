package com.rustyrazorblade.easycasslab.commands

import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.annotations.RequireProfileSetup
import com.rustyrazorblade.easycasslab.configuration.User
import com.rustyrazorblade.easycasslab.services.AWSResourceSetupService
import org.koin.core.component.inject
import picocli.CommandLine.Command

/**
 * Configure AWS infrastructure (IAM roles, S3 bucket) for easy-cass-lab.
 */
@RequireProfileSetup
@Command(
    name = "configure-aws",
    description = ["Configure AWS infrastructure (IAM roles, S3 bucket) for easy-cass-lab"],
)
class ConfigureAWS(
    context: Context,
) : PicoBaseCommand(context) {
    private val awsResourceSetupService: AWSResourceSetupService by inject()
    private val userConfig: User by inject()

    override fun execute() {
        awsResourceSetupService.ensureAWSResources(userConfig)
        outputHandler.handleMessage("✓ AWS infrastructure configured successfully")
    }
}
