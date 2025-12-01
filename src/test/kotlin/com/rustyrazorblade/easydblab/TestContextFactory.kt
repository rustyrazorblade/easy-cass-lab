package com.rustyrazorblade.easydblab

import com.rustyrazorblade.easydblab.configuration.User
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
     *
     * @param workingDirectory Optional working directory for lab operations.
     *                         Use JUnit @TempDir to provide an isolated directory for tests
     *                         that perform file operations (like Clean).
     *                         If null, defaults to the test temp directory.
     */
    fun createTestContext(workingDirectory: File? = null): Context {
        val tmpContentParent = File("test/contexts")
        tmpContentParent.mkdirs()

        val testTempDirectory = Files.createTempDirectory(tmpContentParent.toPath(), "easydblab")
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

        val context =
            Context(
                easyDbLabUserDirectory = testTempDirectory.toFile(),
                workingDirectory = workingDirectory ?: testTempDirectory.toFile(),
            )

        // userConfigFile is accessed through the profileDir
        val userConfigFile = File(context.profileDir, "settings.yaml")
        context.yaml.writeValue(userConfigFile, user)

        return context
    }
}
