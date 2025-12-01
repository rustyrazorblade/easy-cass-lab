package com.rustyrazorblade.easydblab.commands

import com.rustyrazorblade.easydblab.Context
import com.rustyrazorblade.easydblab.annotations.RequireProfileSetup
import com.rustyrazorblade.easydblab.configuration.User
import com.rustyrazorblade.easydblab.services.AWSResourceSetupService
import org.koin.core.component.inject
import picocli.CommandLine.Command

/**
 * Configure AWS infrastructure (IAM roles, S3 bucket) for easy-db-lab.
 */
@RequireProfileSetup
@Command(
    name = "configure-aws",
    description = ["Configure AWS infrastructure (IAM roles, S3 bucket) for easy-db-lab"],
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
