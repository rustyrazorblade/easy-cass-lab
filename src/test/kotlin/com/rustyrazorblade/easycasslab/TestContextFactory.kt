package com.rustyrazorblade.easycasslab

import com.rustyrazorblade.easycasslab.configuration.User
import java.io.File
import java.nio.file.Files

/**
 * Factory for creating test Context instances.
 * This replaces the testContext() method that was previously in the Context companion object.
 */
object TestContextFactory {
    /**
     * Creates a Context instance configured for testing.
     * Creates a temporary directory and a fake user configuration.
     */
    fun createTestContext(): Context {
        val tmpContentParent = File("test/contexts")
        tmpContentParent.mkdirs()

        val testTempDirectory = Files.createTempDirectory(tmpContentParent.toPath(), "easycasslab")
        assert(testTempDirectory != null)

        // Create a default profile with a fake user
        val user =
            User(
                email = "test@rustyrazorblade.com",
                region = "us-west-2",
                keyName = "test",
                sshKeyPath = "test",
                awsProfile = "test",
                awsAccessKey = "test",
                awsSecret = "test",
                axonOpsOrg = "",
                axonOpsKey = "",
                s3Bucket = "",
            )

        val context = Context(testTempDirectory.toFile())

        // userConfigFile is accessed through the profileDir
        val userConfigFile = File(context.profileDir, "settings.yaml")
        context.yaml.writeValue(userConfigFile, user)

        return context
    }
}
