package com.rustyrazorblade.easycasslab.commands

import com.rustyrazorblade.easycasslab.BaseKoinTest
import com.rustyrazorblade.easycasslab.TestContextFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class CleanTest : BaseKoinTest() {
    @TempDir
    lateinit var workingDir: File

    @Test
    fun `clean deletes terraform files from working directory`() {
        // Create test files in temp dir
        val tfState = File(workingDir, "terraform.tfstate")
        val tfStateBackup = File(workingDir, "terraform.tfstate.backup")
        val tfJson = File(workingDir, "terraform.tf.json")

        tfState.createNewFile()
        tfStateBackup.createNewFile()
        tfJson.createNewFile()

        assertThat(tfState).exists()
        assertThat(tfStateBackup).exists()
        assertThat(tfJson).exists()

        val testContext = TestContextFactory.createTestContext(workingDirectory = workingDir)
        Clean(testContext).execute()

        assertThat(tfState).doesNotExist()
        assertThat(tfStateBackup).doesNotExist()
        assertThat(tfJson).doesNotExist()
    }

    @Test
    fun `clean deletes terraform directory from working directory`() {
        // Create .terraform directory
        val terraformDir = File(workingDir, ".terraform")
        terraformDir.mkdir()
        File(terraformDir, "plugins").mkdir()
        File(terraformDir, "plugins/test.txt").createNewFile()

        assertThat(terraformDir).exists()

        val testContext = TestContextFactory.createTestContext(workingDirectory = workingDir)
        Clean(testContext).execute()

        assertThat(terraformDir).doesNotExist()
    }

    @Test
    fun `clean deletes logs directory from working directory`() {
        // Create logs directory with content
        val logsDir = File(workingDir, "logs")
        logsDir.mkdir()
        File(logsDir, "test.log").createNewFile()

        assertThat(logsDir).exists()

        val testContext = TestContextFactory.createTestContext(workingDirectory = workingDir)
        Clean(testContext).execute()

        assertThat(logsDir).doesNotExist()
    }

    @Test
    fun `clean does not delete artifacts directory if it contains files`() {
        // Create artifacts directory with content
        val artifactsDir = File(workingDir, "artifacts")
        artifactsDir.mkdir()
        File(artifactsDir, "important.jar").createNewFile()

        assertThat(artifactsDir).exists()

        val testContext = TestContextFactory.createTestContext(workingDirectory = workingDir)
        Clean(testContext).execute()

        // artifacts should still exist because it contains files
        assertThat(artifactsDir).exists()
    }

    @Test
    fun `clean deletes empty artifacts directory`() {
        // Create empty artifacts directory
        val artifactsDir = File(workingDir, "artifacts")
        artifactsDir.mkdir()

        assertThat(artifactsDir).exists()

        val testContext = TestContextFactory.createTestContext(workingDirectory = workingDir)
        Clean(testContext).execute()

        // artifacts should be deleted because it's empty
        assertThat(artifactsDir).doesNotExist()
    }

    @Test
    fun `clean operates only on specified working directory`() {
        // Create a file in working directory
        val workingDirFile = File(workingDir, "terraform.tfstate")
        workingDirFile.createNewFile()

        // Create another temp directory to simulate project root
        val projectRoot = File(workingDir.parentFile, "simulated-project-root")
        projectRoot.mkdir()
        val projectRootFile = File(projectRoot, "terraform.tfstate")
        projectRootFile.createNewFile()

        try {
            val testContext = TestContextFactory.createTestContext(workingDirectory = workingDir)
            Clean(testContext).execute()

            // Working directory file should be deleted
            assertThat(workingDirFile).doesNotExist()

            // Project root file should NOT be deleted (different directory)
            assertThat(projectRootFile).exists()
        } finally {
            // Cleanup
            projectRootFile.delete()
            projectRoot.delete()
        }
    }
}
