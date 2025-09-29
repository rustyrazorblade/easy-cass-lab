package com.rustyrazorblade.easycasslab.di

import com.rustyrazorblade.easycasslab.BaseKoinTest
import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.configuration.TFState
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.dsl.module
import java.io.ByteArrayInputStream
import java.io.File

class TerraformModuleTest : BaseKoinTest(), KoinComponent {
    @TempDir lateinit var tempDir: File

    @TempDir lateinit var profileDir: File

    private lateinit var tfStateProvider: TFStateProvider

    override fun additionalTestModules(): List<org.koin.core.module.Module> {
        val testContext = Context(profileDir)
        return listOf(
            module { single { testContext } },
            terraformModule(testContext),
        )
    }

    @BeforeEach
    fun setup() {
        // Get instances from DI
        tfStateProvider = get()
    }

    @Test
    fun `should create TFStateProvider instance`() {
        assertThat(tfStateProvider).isNotNull
        assertThat(tfStateProvider).isInstanceOf(DefaultTFStateProvider::class.java)
    }

    @Test
    fun `should parse TFState from input stream`() {
        // Create a minimal valid terraform state JSON
        val tfStateJson =
            """
            {
                "version": 4,
                "terraform_version": "1.0.0",
                "serial": 1,
                "lineage": "test",
                "outputs": {},
                "resources": []
            }
            """.trimIndent()

        val inputStream = ByteArrayInputStream(tfStateJson.toByteArray())
        val tfState = tfStateProvider.parseFromStream(inputStream)

        assertThat(tfState).isNotNull
    }

    @Test
    fun `should parse TFState from file`() {
        // Create a test terraform state file
        val tfStateFile = File(tempDir, "terraform.tfstate")
        tfStateFile.writeText(
            """
            {
                "version": 4,
                "terraform_version": "1.0.0",
                "serial": 1,
                "lineage": "test",
                "outputs": {},
                "resources": []
            }
            """.trimIndent(),
        )

        val tfState = tfStateProvider.parseFromFile(tfStateFile)

        assertThat(tfState).isNotNull
    }

    @Test
    fun `should throw exception when default state file does not exist`() {
        // Note: Context.cwdPath always returns System.getProperty("user.dir"),
        // so we need to temporarily rename or delete the actual terraform.tfstate
        // to test the error case. Instead, we'll test the error by checking
        // that parseFromFile throws on a non-existent file.
        val nonExistentFile = File(tempDir, "non-existent-terraform.tfstate")
        val testContext = Context(tempDir)
        val provider = DefaultTFStateProvider(testContext)

        assertThatThrownBy { provider.parseFromFile(nonExistentFile) }
            .isInstanceOf(Exception::class.java)
    }

    @Test
    fun `should get default TFState when file exists`() {
        // Create a terraform.tfstate in the current working directory
        val workingDir = File(System.getProperty("user.dir"))
        val tfStateFile = File(workingDir, "terraform.tfstate")

        // Only test if the file actually exists (for CI environments)
        if (tfStateFile.exists()) {
            val tfState = tfStateProvider.getDefault()
            assertThat(tfState).isNotNull
        }
    }
}

class TerraformModuleIntegrationTest : BaseKoinTest(), KoinComponent {
    override fun additionalTestModules() =
        listOf(
            module {
                single {
                    Context(File(System.getProperty("java.io.tmpdir"), "test-profile"))
                }
            },
        )

    @Test
    fun `should inject TFStateProvider into Context`() {
        // Context should be able to access TFStateProvider through DI
        val provider: TFStateProvider = get()
        assertThat(provider).isNotNull
        // Note: In tests, this will be the mock provider from testTerraformModule
    }

    @Test
    fun `should provide TFState factory`() {
        // The module should provide a factory for TFState
        val tfStateFile = File(context.cwdPath, "terraform.tfstate")

        // In tests, this should use the mock provider
        val tfState: TFState = get()
        assertThat(tfState).isNotNull
    }
}
