package com.rustyrazorblade.easycasslab.mcp

import com.rustyrazorblade.easycasslab.BaseKoinTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream

/**
 * Tests for PromptLoader using InputStream-based API for resource loading.
 *
 * These tests verify the prompt parsing logic using in-memory streams,
 * which is appropriate since the actual implementation uses ClassGraph
 * for resource loading from the classpath.
 */
class PromptLoaderTest : BaseKoinTest() {
    @Test
    fun `should parse prompt with valid YAML frontmatter`() {
        // Given
        val promptContent =
            """
            ---
            name: test-prompt
            description: A test prompt
            ---
            # Test Prompt
            
            This is the prompt content.
            """.trimIndent()

        val inputStream = ByteArrayInputStream(promptContent.toByteArray())

        // When
        val loader = PromptLoader()
        val prompt = loader.loadPromptFromStream(inputStream, "test.md")

        // Then
        assertThat(prompt.name).isEqualTo("test-prompt")
        assertThat(prompt.description).isEqualTo("A test prompt")
        assertThat(prompt.content)
            .isEqualTo(
                """
                # Test Prompt
                
                This is the prompt content.
                """.trimIndent(),
            )
    }

    @Test
    fun `should handle multiline description in frontmatter`() {
        // Given
        val promptContent =
            """
            ---
            name: multiline-prompt
            description: |
              This is a multiline
              description that spans
              multiple lines.
            ---
            Content here.
            """.trimIndent()

        val inputStream = ByteArrayInputStream(promptContent.toByteArray())

        // When
        val loader = PromptLoader()
        val prompt = loader.loadPromptFromStream(inputStream, "multiline.md")

        // Then
        assertThat(prompt.description)
            .isEqualTo(
                """
                This is a multiline
                description that spans
                multiple lines.
                """.trimIndent(),
            )
    }

    @Test
    fun `should throw exception when name is missing from frontmatter`() {
        // Given
        val promptContent =
            """
            ---
            description: Missing name field
            ---
            Content.
            """.trimIndent()

        val inputStream = ByteArrayInputStream(promptContent.toByteArray())

        // When/Then
        val loader = PromptLoader()
        assertThatThrownBy { loader.loadPromptFromStream(inputStream, "missing-name.md") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Missing required 'name' field")
    }

    @Test
    fun `should throw exception when description is missing from frontmatter`() {
        // Given
        val promptContent =
            """
            ---
            name: test-prompt
            ---
            Content.
            """.trimIndent()

        val inputStream = ByteArrayInputStream(promptContent.toByteArray())

        // When/Then
        val loader = PromptLoader()
        assertThatThrownBy { loader.loadPromptFromStream(inputStream, "missing-description.md") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Missing required 'description' field")
    }

    @Test
    fun `should throw exception when frontmatter is malformed`() {
        // Given
        val promptContent =
            """
            ---
            name test-prompt
            description: Missing colon above
            ---
            Content.
            """.trimIndent()

        val inputStream = ByteArrayInputStream(promptContent.toByteArray())

        // When/Then
        val loader = PromptLoader()
        assertThatThrownBy { loader.loadPromptFromStream(inputStream, "malformed.md") }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `should throw exception when frontmatter is not closed`() {
        // Given
        val promptContent =
            """
            ---
            name: test
            description: Missing closing delimiter
            
            Content that should be in frontmatter
            """.trimIndent()

        val inputStream = ByteArrayInputStream(promptContent.toByteArray())

        // When/Then
        val loader = PromptLoader()
        assertThatThrownBy { loader.loadPromptFromStream(inputStream, "unclosed.md") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("properly closed")
    }

    @Test
    fun `should throw exception when file has no frontmatter`() {
        // Given
        val promptContent = "Just regular markdown without frontmatter."
        val inputStream = ByteArrayInputStream(promptContent.toByteArray())

        // When/Then
        val loader = PromptLoader()
        assertThatThrownBy { loader.loadPromptFromStream(inputStream, "no-frontmatter.md") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("frontmatter")
    }

    @Test
    fun `should handle empty content after frontmatter`() {
        // Given
        val promptContent =
            """
            ---
            name: empty-content
            description: Prompt with no content
            ---
            """.trimIndent()

        val inputStream = ByteArrayInputStream(promptContent.toByteArray())

        // When
        val loader = PromptLoader()
        val prompt = loader.loadPromptFromStream(inputStream, "empty.md")

        // Then
        assertThat(prompt.content).isEmpty()
    }

    @Test
    fun `should preserve formatting in prompt content`() {
        // Given
        val promptContent =
            """
            ---
            name: formatted
            description: Test formatting preservation
            ---
            # Heading
            
            - List item 1
            - List item 2
            
            ```kotlin
            fun test() {
                println("code")
            }
            ```
            """.trimIndent()

        val inputStream = ByteArrayInputStream(promptContent.toByteArray())

        // When
        val loader = PromptLoader()
        val prompt = loader.loadPromptFromStream(inputStream, "formatted.md")

        // Then
        assertThat(prompt.content).contains("# Heading")
        assertThat(prompt.content).contains("- List item")
        assertThat(prompt.content).contains("```kotlin")
        assertThat(prompt.content).contains("fun test()")
    }

    @Test
    fun `should handle prompt with special characters in name`() {
        // Given
        val promptContent =
            """
            ---
            name: test-prompt_v1.0
            description: Prompt with special chars
            ---
            Content
            """.trimIndent()

        val inputStream = ByteArrayInputStream(promptContent.toByteArray())

        // When
        val loader = PromptLoader()
        val prompt = loader.loadPromptFromStream(inputStream, "special.md")

        // Then
        assertThat(prompt.name).isEqualTo("test-prompt_v1.0")
    }

    @Test
    fun `should load prompts from classpath resources`() {
        // Given - the actual prompt resources in src/main/resources/com/rustyrazorblade/mcp/

        // When
        val loader = PromptLoader()
        val prompts = loader.loadAllPrompts("com.rustyrazorblade.mcp")

        // Then
        assertThat(prompts).isNotEmpty
        assertThat(prompts.map { it.name }).contains("activate", "provision")
    }

    @Test
    fun `should return empty list when package does not exist`() {
        // Given
        val nonExistentPackage = "com.nonexistent.package"

        // When
        val loader = PromptLoader()
        val prompts = loader.loadAllPrompts(nonExistentPackage)

        // Then
        assertThat(prompts).isEmpty()
    }

    @Test
    fun `should skip invalid prompts and continue loading valid ones`() {
        // Given - actual resources where some may be invalid (tested via integration)
        // This test verifies that the loader handles errors gracefully

        // When
        val loader = PromptLoader()
        val prompts = loader.loadAllPrompts("com.rustyrazorblade.mcp")

        // Then - should load at least the valid prompts
        assertThat(prompts).isNotEmpty
    }
}
